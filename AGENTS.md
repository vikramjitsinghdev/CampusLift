# CampusLift Development Rules

CampusLift is a native Android application.

## Required stack
- Kotlin only for Android application logic.
- Android XML layouts unless an existing screen already uses another native Android UI approach.
- Room for local persistent application data.
- JSON files for seed/demo/configuration data.
- OpenRouteService for route/path calculation.
- Android Studio/Gradle compatible project structure.

## Coding rules
- Inspect existing files before creating replacements.
- Reuse existing AppDatabase, DAO, entities, resources, and layouts when practical.
- Do not rewrite the entire project unless absolutely necessary.
- Prefer Android/native solutions over adding unnecessary dependencies.
- Keep code understandable for a CS2063 university project.
- Do not introduce Firebase.
- Do not implement authentication unless explicitly requested.
- Do not hardcode API keys.
- Never claim a feature works unless it was actually verified.

## Verification
After making significant changes:
- run the Gradle build
- inspect compiler errors
- fix errors introduced by your changes
- report what was actually verified

## Data responsibilities
JSON = initial structured seed data.
Room = local persistent runtime data.
OpenRouteService = route calculation.
Android UI/map = presentation.