# 📦 Siemens Java Internship

This Spring Boot application provides a REST API for managing items. It supports full CRUD operations, input validation, centralized error handling, and asynchronous processing. The original codebase lacked structure and test coverage. This refactored version is stable, modular, and fully tested.

---

## ✅ Functionalities

- **CRUD Operations**
  - `GET /api/items` — List all items
  - `GET /api/items/{id}` — Get item by ID
  - `POST /api/items` — Create a new item with validation
  - `PUT /api/items/{id}` — Update an existing item
  - `DELETE /api/items/{id}` — Delete an item by ID

- **Asynchronous Processing**
  - `GET /api/items/process` — Asynchronously updates the status of all items to `PROCESSED`
  - Only successful updates are returned
  - Failures are logged and skipped without stopping the process

- **Validation**
  - Uses standard annotations like `@NotBlank`, `@Size`, `@Pattern`
  - Implements a custom `@ValidEmail` annotation with:
    - Regex validation
    - MX record lookup via DNS

- **Error Handling**
  - All exceptions handled in `GlobalExceptionHandler`
  - Returns structured `ErrorResponse` JSON with:
    - Status code
    - Error type
    - List of messages
    - Request path

- **Testing**
  - Full test coverage for:
    - Controllers (via MockMvc)
    - Services (via unit tests and mocking)
    - Validators (including edge cases)
    - Exception handler
  - Achieves 100% line and branch coverage

---

## 🛠️ What I Refactored

- Organized code into logical packages: `controller`, `model`, `service`, `repository`
- Refactored `processItemsAsync()` to:
  - Avoid crashing on individual failures
  - Aggregate successful results only
- Created reusable `ErrorResponse` model
- Wrote integration tests for all endpoints and validation errors
- Implemented custom `ConstraintValidatorFactory` in tests for email validation bypassing DNS calls
- Improved documentation and maintainability

---

This project is refactored, tested, and documented according to the internship assignment.  
GitHub Repository: [https://github.com/SiemensJava2025/SiemensJava2025]

---
