type UUID = string;
type DateString = string; // YYYY-MM-DD
type Timestamp = string; // ISO 8601, UTC
type Employment = "EMPLOYED" | "STUDENT" | "JOB_SEEKER" | "OTHER";
type HousingType = "JEONSE" | "MONTHLY" | "PUBLIC_RENTAL";
type Preset = "BALANCED" | "COST" | "COMMUTE" | "NEIGHBORHOOD" | "CUSTOM";
export type PriorityPreset = Exclude<Preset, "CUSTOM">;
export type ComparisonAnswer = {
  status:AnswerStatus; answer:string; answerSource:AnswerSource;
  references:{homeId:UUID; field:string; value:string|number|boolean|null}[];
  comparisonId:UUID; inputRevision:number;
};
type Weights = { cost:number; commute:number; convenience:number };
type Location = { name:string; address:string; longitude:number; latitude:number };
type Progress = {
  answeredCount:number; requiredCount:number; missingFields:string[];
  canEvaluate:boolean; canCompare:boolean;
};
export type Profile = {
  birthYear:number|null;
  birthdayPassedThisYear:boolean|null;
  birthdayAnswerAsOfDate:DateString|null;
  employmentStatus:Employment|null;
  frequentPlace:Location|null;
  availableCashWon:number|null;
  income:{period:"MONTHLY"|"ANNUAL"; amountWon:number}|null;
  commuteLimit:{type:"LIMIT"; minutes:number}|{type:"NO_LIMIT"}|null;
  moveIn:{type:"THIS_MONTH"|"NEXT_MONTH"|"WITHIN_THREE_MONTHS"|"UNDECIDED";
    asOfDate:DateString; startMonth:string|null; endMonth:string|null}|null;
  ownsHome:boolean|null;
  parentAddressStatus:"SAME"|"DIFFERENT"|"UNKNOWN"|null;
  age:{minYears:number; maxYears:number; exactYears:number|null}|null;
  normalizedMonthlyIncomeWon:number|null;
};
export type Home = {
  homeId:UUID; slot:1|2|3; label:string; location:Location;
  depositWon:number; monthlyRentWon:number; maintenanceWon:number|null;
  housingType:HousingType|null; exclusiveAreaM2:number|null;
  inputMethod:"MANUAL";
};
export type Session = {
  sessionId:UUID; assessmentDate:DateString; revision:number; expiresAt:Timestamp|null;
  profile:Profile; progress:Progress; homes:Home[];
  preferences:{priorityPreset:Preset; weights:Weights};
  latestComparisonId:UUID|null;
};
export type LocationSearch = {
  items:{locationId:string; name:string; roadAddress:string|null;
    parcelAddress:string|null; longitude:number|null; latitude:number|null;
    selectable:boolean; reason:string|null}[];
  dataSource:string;
};
export type Eligibility = "MET"|"NOT_MET"|"CHECK_NEEDED";
export type ApplicationStatus = "OPEN"|"ALWAYS"|"UPCOMING"|"CLOSED"|"UNKNOWN";
type Evidence = {id:string; policyKey:string; title:string; excerpt:string;
  sourceUrl:string|null; locator:string|null};
type Condition = {key:string; label:string; actual:string|null; expected:string|null;
  status:Eligibility; reason:string; evidenceIds:string[]};
type Benefit = {monthlyWon:number|null; durationMonths:number|null;
  oneTimeWon:number|null; potentialTotalWon:number|null};
type Schedule = {kind:string; title:string; policyKey:string|null;
  startDate:DateString|null; endDate:DateString|null; evidenceIds:string[]};
export type Policy = {
  policyKey:string; name:string;
  group:"APPLY_NOW"|"UPCOMING"|"CHECK_NEEDED"|"UNAVAILABLE";
  eligibilityStatus:Eligibility; applicationStatus:ApplicationStatus;
  conditions:Condition[]; benefit:Benefit;
  rounds:{roundId:string; status:ApplicationStatus; startDate:DateString|null;
    endDate:DateString|null; sourceUrl:string|null}[];
  methodText:string|null; sourceUrl:string|null; evidence:Evidence[];
};
export type PolicyEvaluation = {
  inputRevision:number; assessmentDate:DateString; calculationVersion:string;
  summary:{potentialTotalWon:number|null; totalStatus:"CALCULATED"|"PARTIAL"|"UNKNOWN";
    includedPolicyKeys:string[]; targetYear:number};
  policies:Policy[]; calendar:Schedule[]; checklist:Schedule[]; evidence:Evidence[];
  conflicts:{includedPolicyKey:string; excludedPolicyKey:string; reason:string; evidenceIds:string[]}[];
  delta:{baseTotalWon:number|null; scenarioTotalWon:number|null;
    addedPolicyKeys:string[]; removedPolicyKeys:string[]; changedPolicyKeys:string[]}|null;
};
type AnswerStatus = "ANSWERED"|"NO_EVIDENCE"|"OUT_OF_SCOPE"|"FALLBACK";
type AnswerSource = "GROUNDED"|"RULE"|"SOURCE_EXCERPT"|"PREPARED_ANSWER"|null;
export type PolicyAnswer = {status:AnswerStatus; answer:string; answerSource:AnswerSource;
  evidence:Evidence[]; helpLinks:{label:string; url:string}[];
  inputRevision:number; assessmentDate:DateString};
export type ProfilePatch = {
  birthYear?:number|null;
  birthdayPassedThisYear?:boolean|null;
  employmentStatus?:Employment|null;
  frequentPlace?:{locationId:string}|null;
  availableCashWon?:number|null;
  income?:{period:"MONTHLY"|"ANNUAL";amountWon:number}|null;
  commuteLimit?:{type:"LIMIT";minutes:number}|{type:"NO_LIMIT"}|null;
  moveIn?:{type:"THIS_MONTH"|"NEXT_MONTH"|"WITHIN_THREE_MONTHS"|"UNDECIDED"}|null;
  ownsHome?:boolean|null;
  parentAddressStatus?:"SAME"|"DIFFERENT"|"UNKNOWN"|null;
};

type Warning = { code:string; field:string|null; message:string };
export type Route = {
  status:"OK"|"ESTIMATED"|"NOT_FOUND"|"UNAVAILABLE";
  mode:"TRANSIT"|"WALK"|null; source:string|null;
  durationSeconds:number|null; distanceMeters:number|null;
  oneWayFareWon:number|null; overLimitSeconds:number|null;
  segments:{mode:"WALK"|"BUS"|"SUBWAY"; lineName:string|null; durationSeconds:number|null}[];
  geometry:{type:"LineString"; coordinates:[number,number][]}|null;
  queriedAt:Timestamp;
};
export type Facility = {
  category:"SUBWAY"|"LARGE_MART"|"SUPERMARKET"|"CONVENIENCE_STORE"|"PHARMACY"|"INTERNAL_MEDICINE"|"GYM"|"PARK";
  status:"FOUND"|"NOT_FOUND"|"UNAVAILABLE"; name:string|null; location:Location|null;
  distanceMeters:number|null; walkMinutes:number|null; walkTimeSource:string|null;
  searchRadiusMeters:number;
};
export type HomePreview = {previewId:string; expiresAt:Timestamp; location:Location;
  route:Route; facilities:Facility[]; warnings:Warning[]};
type Cost = {depositWon:number; depositShortfallWon:number|null;
  monthlyRentWon:number; maintenanceWon:number|null; monthlyHousingCostWon:number;
  excludedUnknownMaintenance:boolean; monthlyAfterSupportWon:number|null;
  monthlyTransportFareWon:number|null};
export type ComparisonHome = {
  homeId:UUID; slot:1|2|3; label:string; location:Location; cost:Cost;
  route:Route; facilities:Facility[];
  policyResults:{policyKey:string; eligibilityStatus:Eligibility;
    applicationStatus:ApplicationStatus; conditions:Condition[];
    benefit:Benefit; reason:string; evidence:Evidence[]}[];
  scores:{cost:number|null; commute:number|null; convenience:number|null; total:number|null};
  rank:number|null;
};
export type Comparison = {
  comparisonId:UUID; inputRevision:number; assessmentDate:DateString;
  calculationVersion:string; status:"COMPLETE"|"PARTIAL"; isCurrent:boolean;
  priorityPreset:Preset; weights:Weights; homes:ComparisonHome[];
  recommendation:{homeId:UUID|null; reasons:string[]; tradeoffs:string[]; rankedHomeIds:UUID[]};
  warnings:Warning[]; calculatedAt:Timestamp;
};
export type HomePut = {previewId:string; label?:string; depositWon:number;
  monthlyRentWon:number; maintenanceWon:number|null; housingType?:HousingType|null;
  exclusiveAreaM2?:number|null; inputMethod?:"MANUAL"};
