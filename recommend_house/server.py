"""Private HTTP adapter for the existing recommendation engine."""
import os

from flask import Flask, jsonify, request
from waitress import serve

from recommendation import House, recommend

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 32 * 1024


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/v1/recommendations")
def recommendations():
    data = request.get_json()
    if not isinstance(data, dict) or not isinstance(data.get("houses"), list):
        return {"error": "houses must be a list"}, 400
    try:
        houses = [House(**h) for h in data["houses"]]
        result = recommend(houses, data["priority"], weights=data.get("weights"),
                           max_commute_minutes=data.get("maxCommuteMinutes"))
    except (TypeError, ValueError, KeyError) as exc:
        return {"error": str(exc)}, 400
    ranking = {item.id: (rank, item) for rank, item in enumerate(result.ranked, 1)}
    rows = []
    for house in houses:
        row = {"id": house.id, "rank": None, "scores": None,
               "missing": result.missing_fields.get(house.id, ()),
               "excluded": result.excluded_fields.get(house.id, ()),
               "estimated": result.estimated_fields.get(house.id, ())}
        if house.id in ranking:
            row["rank"], item = ranking[house.id]
            row["scores"] = {key: round(value) for key, value in (
                ("cost", item.cost_score), ("commute", item.time_score),
                ("convenience", item.convenience_score), ("total", item.total_score))}
        rows.append(row)
    return jsonify(homes=rows)


if __name__ == "__main__":
    serve(app, host=os.getenv("HOST", "127.0.0.1"), port=int(os.getenv("PORT", "8001")))
