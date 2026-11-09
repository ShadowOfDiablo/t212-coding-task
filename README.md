# Auto Trading Bot

A simulated trading bot: a Java Spring Boot backend that runs the trading logic, and a React frontend that displays it.

## Project structure

```
.
├── README.md
├── pom.xml            aggregator: open the repository root in an IDE and the backend is picked up
├── frontend/          React UI (not set up yet)
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

### Code quality

Code follows the [Google Java Style](https://google.github.io/styleguide/javaguide.html).

```bash
./mvnw verify          # tests + format check (Spotless) + Checkstyle + SpotBugs
./mvnw spotless:apply  # format the code
```

## Frontend

Not set up yet. Setup and run instructions will be added together with the React project.
