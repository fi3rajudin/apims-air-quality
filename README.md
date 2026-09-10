# APIMS Air Quality

A small Spring Boot project that reads current Malaysia DOE APIMS station data from the public endpoint used by the APIMS website and exposes a cleaner API plus a simple browser UI.

## Stack

- Java 17
- Spring Boot 4.1.1
- Maven
- Spring `RestClient`
- Plain HTML/CSS/JavaScript frontend served by Spring Boot

## Open in VS Code

1. Install JDK 17 or newer.
2. Install Maven if `mvn` is not already available.
3. In VS Code, choose **File > Open Folder** and open this project folder.
4. Install the recommended Java and Spring extensions when VS Code prompts you.
5. Run `ApimsAirQualityApplication.java`, or run:

```bash
mvn spring-boot:run
```

6. Open:

```text
http://localhost:8080
```

The backend endpoint is:

```text
GET http://localhost:8080/api/readings
GET http://localhost:8080/api/readings?state=Selangor
```

Health check:

```text
GET http://localhost:8080/actuator/health
```

## Project structure

```text
src/main/java/com/fit/apims/
├── ApimsAirQualityApplication.java
├── config/
│   ├── AppConfig.java
│   └── DoeApiProperties.java
├── controller/
│   └── AirQualityController.java
├── model/
│   └── AirQualityReading.java
└── service/
    └── DoeApimsService.java

src/main/resources/
├── application.properties
└── static/
    ├── index.html
    ├── styles.css
    └── app.js
```

## Important note about the DOE endpoint

The upstream URL is configurable in `src/main/resources/application.properties` under `doe.apims.url`.

This project deliberately keeps the DOE integration inside `DoeApimsService`. If DOE changes the endpoint or response format later, you only need to update that integration layer instead of changing the UI everywhere.

A five-minute in-memory cache is included to avoid repeatedly hitting the DOE service for every browser request. Change `doe.apims.cache-ttl` if needed.

## Good next features

- Map monitoring stations with latitude/longitude.
- Find the nearest station using browser geolocation.
- Show API categories and health guidance.
- Add historical charts if a historical source can be identified.
- Convert the UI to React/Vue later without changing the backend contract.
- Add persistence if you want to build your own historical dataset from periodic readings.

## Deploy on Render

This project includes a `Dockerfile` and `render.yaml` for Render.

Before deploying, commit the project to a Git repository. In Render, create a Blueprint from the repository (or create a Web Service using the Docker runtime). The included Blueprint uses the Free plan and `/actuator/health` as the health check.

Recommended production environment variable:

```text
GEOCODING_USER_AGENT=RainCheck-APIMS/1.0
```

If you later have a public project URL or contact page, make the User-Agent more specific so it clearly identifies the application.

The application binds to Render's `PORT` environment variable automatically.

### Upstream-service notes

DOE APIMS data and OpenStreetMap Nominatim are external dependencies. The application caches DOE readings for five minutes and geocoding results for 24 hours. Nominatim requests are serialized to no more than roughly one request per second. Do not add client-side autocomplete against the public Nominatim service.
