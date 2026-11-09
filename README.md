# Auto Trading Bot

A simulated trading bot: a Java Spring Boot backend that runs the trading logic, and a plain HTML/JavaScript frontend that displays it.

## Project structure

```
.
├── README.md
├── pom.xml            aggregator: open the repository root in an IDE and the backend is picked up
├── frontend/          static UI (HTML, CSS, JavaScript)
│   ├── index.html
│   ├── app.js
│   ├── styles.css
│   └── charts.js
└── backend/           Java Spring Boot project (Maven)
```

## Prerequisites

- Java 21 (JDK)
- No Maven installation needed: the backend ships with the Maven wrapper (`mvnw` / `mvnw.cmd`), which downloads the correct Maven version on first use.

## Backend

All commands are run from the `backend/` folder.

Run the tests:

```bash
./mvnw test          # macOS / Linux / Git Bash
.\mvnw.cmd test      # Windows PowerShell / cmd
```

Start the application:

```bash
./mvnw spring-boot:run
.\mvnw.cmd spring-boot:run
```

Build a runnable jar:

```bash
./mvnw package
java -jar target/t212-spring-boot-project-0.0.1-SNAPSHOT.jar
```

## Frontend

The frontend is static and needs no build step. Open `frontend/index.html` in a browser.
