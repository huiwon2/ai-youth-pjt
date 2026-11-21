package com.aiyouth.pjt.mapper;

import com.aiyouth.pjt.model.PlanningRows.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PlanningMapper {
    @Insert("""
            INSERT INTO planning_sessions
              (id, access_token_hash, assessment_date, priority_preset, score_weights,
               facility_preferences, accessibility_preferences, expires_at, created_at, updated_at)
            VALUES (#{id}, #{hash}, #{date}, 'BALANCED', '{"cost":40,"commute":35,"convenience":25}',
                    '[]', '{}', #{expiresAt}, #{now}, #{now})
            """)
    void insertSession(@Param("id") UUID id, @Param("hash") String hash, @Param("date") LocalDate date,
                       @Param("expiresAt") OffsetDateTime expiresAt, @Param("now") OffsetDateTime now);

    @Select("""
            SELECT id, assessment_date, revision, priority_preset, score_weights::text, expires_at
            FROM planning_sessions
            WHERE id = #{id} AND access_token_hash = #{hash}
            FOR UPDATE
            """)
    SessionRow lockOwnedSession(@Param("id") UUID id, @Param("hash") String hash);

    @Select(value = """
            UPDATE planning_sessions
            SET assessment_date = #{date}, revision = revision + 1, updated_at = #{now}
            WHERE id = #{id}
            RETURNING revision
            """, affectData = true)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    int advanceRevision(@Param("id") UUID id, @Param("date") LocalDate date, @Param("now") OffsetDateTime now);

    @Select("""
            SELECT session_id, age_years, age_as_of_date, employment_status, available_cash_won,
                   monthly_income_won, max_commute_minutes, desired_move_in_month, additional_answers::text,
                   confirmed_at FROM profiles WHERE session_id = #{id}
            """)
    ProfileRow findProfile(UUID id);

    @Insert("""
            INSERT INTO profiles
              (session_id, age_years, age_as_of_date, employment_status, available_cash_won,
               monthly_income_won, max_commute_minutes, desired_move_in_month, additional_answers,
               confirmed_at, created_at, updated_at)
            VALUES (#{row.sessionId}, #{row.ageYears}, #{row.ageAsOfDate}, #{row.employmentStatus},
                    #{row.availableCashWon}, #{row.monthlyIncomeWon}, #{row.maxCommuteMinutes},
                    #{row.desiredMoveInMonth}, CAST(#{row.additionalAnswers} AS jsonb), #{row.confirmedAt},
                    #{now}, #{now})
            ON CONFLICT (session_id) DO UPDATE SET
              age_years = EXCLUDED.age_years, age_as_of_date = EXCLUDED.age_as_of_date,
              employment_status = EXCLUDED.employment_status, available_cash_won = EXCLUDED.available_cash_won,
              monthly_income_won = EXCLUDED.monthly_income_won, max_commute_minutes = EXCLUDED.max_commute_minutes,
              desired_move_in_month = EXCLUDED.desired_move_in_month, additional_answers = EXCLUDED.additional_answers,
              confirmed_at = EXCLUDED.confirmed_at, updated_at = EXCLUDED.updated_at
            """)
    void saveProfile(@Param("row") ProfileRow row, @Param("now") OffsetDateTime now);

    @Select("""
            SELECT session_id, name, confirmed_address, location_details::text, destination_version
            FROM frequent_places WHERE session_id = #{id}
            """)
    PlaceRow findPlace(UUID id);

    @Insert("""
            INSERT INTO frequent_places
              (session_id, name, confirmed_address, location_details, destination_version,
               confirmed_at, created_at, updated_at)
            VALUES (#{row.sessionId}, #{row.name}, #{row.confirmedAddress}, CAST(#{row.locationDetails} AS jsonb),
                    #{row.destinationVersion}, #{now}, #{now}, #{now})
            ON CONFLICT (session_id) DO UPDATE SET name = EXCLUDED.name,
              confirmed_address = EXCLUDED.confirmed_address, location_details = EXCLUDED.location_details,
              destination_version = EXCLUDED.destination_version, confirmed_at = EXCLUDED.confirmed_at,
              updated_at = EXCLUDED.updated_at
            """)
    void savePlace(@Param("row") PlaceRow row, @Param("now") OffsetDateTime now);

    @Delete("DELETE FROM frequent_places WHERE session_id = #{id}")
    void deletePlace(UUID id);

    @Select("""
            SELECT id, session_id, slot_no, label, confirmed_address, housing_type, deposit_won,
                   monthly_rent_won, maintenance_won, exclusive_area_m2, input_method,
                   location_details::text, lookup_details::text, route_destination_version
            FROM candidate_homes WHERE session_id = #{id} ORDER BY slot_no
            """)
    List<HomeRow> findHomes(UUID id);

    @Insert("""
            INSERT INTO candidate_homes
              (id, session_id, slot_no, label, confirmed_address, housing_type, deposit_won, monthly_rent_won,
               maintenance_won, exclusive_area_m2, input_method, field_provenance, confirmed_at, created_at,
               updated_at, location_details, lookup_details, route_destination_version)
            VALUES (#{row.id}, #{row.sessionId}, #{row.slotNo}, #{row.label}, #{row.confirmedAddress},
                    #{row.housingType}, #{row.depositWon}, #{row.monthlyRentWon}, #{row.maintenanceWon},
                    #{row.exclusiveAreaM2}, #{row.inputMethod}, CAST(#{provenance} AS jsonb), #{now}, #{now},
                    #{now}, CAST(#{row.locationDetails} AS jsonb), CAST(#{row.lookupDetails} AS jsonb),
                    #{row.routeDestinationVersion})
            ON CONFLICT (session_id, slot_no) DO UPDATE SET
              id = EXCLUDED.id, label = EXCLUDED.label, confirmed_address = EXCLUDED.confirmed_address,
              housing_type = EXCLUDED.housing_type, deposit_won = EXCLUDED.deposit_won,
              monthly_rent_won = EXCLUDED.monthly_rent_won, maintenance_won = EXCLUDED.maintenance_won,
              exclusive_area_m2 = EXCLUDED.exclusive_area_m2, input_method = EXCLUDED.input_method,
              field_provenance = EXCLUDED.field_provenance, confirmed_at = EXCLUDED.confirmed_at,
              created_at = CASE WHEN candidate_homes.id = EXCLUDED.id THEN candidate_homes.created_at
                                ELSE EXCLUDED.created_at END,
              updated_at = EXCLUDED.updated_at, location_details = EXCLUDED.location_details,
              lookup_details = EXCLUDED.lookup_details, route_destination_version = EXCLUDED.route_destination_version
            """)
    void saveHome(@Param("row") HomeRow row, @Param("provenance") String provenance, @Param("now") OffsetDateTime now);

    @Update("""
            UPDATE candidate_homes
            SET lookup_details = CAST(#{lookup} AS jsonb), route_destination_version = #{destinationVersion}
            WHERE id = #{id} AND lookup_details IS NOT DISTINCT FROM CAST(#{previousLookup} AS jsonb)
              AND route_destination_version IS NOT DISTINCT FROM CAST(#{previousDestinationVersion} AS uuid)
            """)
    void cacheHomeLookup(@Param("id") UUID id, @Param("lookup") String lookup,
                         @Param("destinationVersion") UUID destinationVersion, @Param("previousLookup") String previousLookup,
                         @Param("previousDestinationVersion") UUID previousDestinationVersion);

    @Delete("DELETE FROM candidate_homes WHERE session_id = #{id} AND slot_no = #{slot}")
    int deleteHome(@Param("id") UUID id, @Param("slot") int slot);

    @Select("""
            SELECT id FROM comparison_runs WHERE session_id = #{id}
            ORDER BY calculated_at DESC, id DESC LIMIT 1
            """)
    UUID latestComparison(UUID id);

    @Insert("""
            INSERT INTO location_choices (id, session_id, kind, details, expires_at)
            VALUES (#{id}, #{sessionId}, #{kind}, CAST(#{details} AS jsonb), #{expiresAt})
            """)
    void insertChoice(ChoiceRow row);

    @Select("""
            SELECT id, session_id, kind, details::text, expires_at FROM location_choices
            WHERE id = #{id} AND session_id = #{sessionId}
            """)
    ChoiceRow findChoice(@Param("sessionId") UUID sessionId, @Param("id") UUID id);

    @Delete("DELETE FROM location_choices WHERE session_id = #{id} AND expires_at <= #{now}")
    void deleteExpiredChoices(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    @Insert("""
            INSERT INTO home_previews (id, session_id, destination_version, details, expires_at)
            VALUES (#{id}, #{sessionId}, #{destinationVersion}, CAST(#{details} AS jsonb), #{expiresAt})
            """)
    void insertPreview(PreviewRow row);

    @Select("""
            SELECT id, session_id, destination_version, details::text, expires_at FROM home_previews
            WHERE id = #{id} AND session_id = #{sessionId}
            """)
    PreviewRow findPreview(@Param("sessionId") UUID sessionId, @Param("id") UUID id);

    @Delete("DELETE FROM home_previews WHERE session_id = #{id} AND expires_at <= #{now}")
    void deleteExpiredPreviews(@Param("id") UUID id, @Param("now") OffsetDateTime now);
}
