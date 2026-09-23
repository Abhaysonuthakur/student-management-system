# Interview Preparation — Student Management System

Everything you need to defend this project in an interview. Part 1 is the concept
questions asked during the build, with model answers. Part 2 is the project explained
end to end. Part 3 is a mock interview you should answer out loud.

---

# Part 1 — Concept questions

## 1. Database design

**Q1. Why use an auto-increment surrogate key instead of `email` as the primary key? What
concretely breaks if you use `email`?**

A primary key must be **immutable, unique, minimal, and never null**. `email` fails
*immutable* and *minimal*.

- **Immutability:** if `email` is the PK and a student changes it, every foreign key
  pointing at that student must be rewritten — a cascading update across every referencing
  table. A surrogate key never changes.
- **Minimality:** `BIGINT` is 8 bytes, `VARCHAR(150)` is up to 150. In InnoDB, **every
  secondary index silently stores a copy of the primary key**, so a fat PK bloats every
  index you own.
- **Collation:** `email` carries comparison rules that make joins slower and comparisons
  subtler.
- Some people have no email address at all.

**Q2. `phone VARCHAR(15)` vs `phone BIGINT` — why is `BIGINT` a bug waiting to happen?**

Leading zeros are destroyed (`0123456789` → `123456789`); the `+91` prefix cannot be stored
at all; and no arithmetic is ever performed on a phone number. **It is an identifier, not a
quantity.** Identifiers are text.

**Q3. You have `UNIQUE(email)` in the database *and* a duplicate check in Java. Do you need
both? What happens if two requests arrive at the same instant with only the Java check?**

You need both, for different reasons.

- The Java check exists for **user experience** — fail fast with a friendly message.
- The database constraint exists for **correctness** — it is the only thing that is
  **atomic**.
- **The race:** two requests arrive in the same millisecond. Both threads run
  `SELECT ... WHERE email = ?`, both see no rows, both pass the Java check, both `INSERT`.
  Your Java check cannot prevent this — it is a **check-then-act** gap. The database
  serialises the two inserts and rejects one with `ERROR 1062`. That is why the DAO must
  also let `SQLIntegrityConstraintViolationException` propagate.

**Q4. Storing `age` is arguably a design mistake. What would you store instead, and how
does the query change?**

Store `date_of_birth DATE`. Age is **derived data**, and derived data that must be
refreshed over time is a bug factory — a scheduled job that fails silently leaves every
student a year younger.

The naive query:

```sql
WHERE TIMESTAMPDIFF(YEAR, date_of_birth, CURDATE()) = 20
```

works but is **non-sargable**: wrapping the column in a function means MySQL cannot use the
index and must scan every row. The index-friendly version computes the date range in Java
first:

```sql
WHERE date_of_birth BETWEEN ? AND ?
```

*Sargable* is the word to use.

**Q5. InnoDB vs MyISAM — name two concrete capabilities you would lose with MyISAM.**

1. **Transactions.** MyISAM has no `BEGIN`/`COMMIT`/`ROLLBACK`, so a half-finished
   multi-table write is unrecoverable.
2. **Foreign keys.** MyISAM parses `FOREIGN KEY` and silently ignores it.

(Bonus: MyISAM locks the whole table for writes; InnoDB locks individual rows, so MyISAM
kills concurrency.)

**Q6. What does `utf8mb4_0900_ai_ci` mean, and what did it change in this project?**

`utf8mb4` is real 4-byte UTF-8 — MySQL's legacy `utf8` is a 3-byte lie that cannot store
emoji. `0900_ai_ci` is the collation: `ai` = accent-insensitive, `ci` = **case**-insensitive.

In this project it means `search("java")` matches `'Java'` and `PROBE@example.com` collides
with `probe@example.com` **without a single line of Java doing anything**. That is a
database decision leaking upward — and exactly what breaks when someone "fixes" the
collation later.

---

## 2. SQL

**Q7. What is the single most dangerous mistake when writing `UPDATE` or `DELETE`?**

Omitting the `WHERE` clause. `UPDATE students SET course = 'X'` rewrites every row;
`DELETE FROM students` empties the table. Both are syntactically valid and both succeed.
The habit that saves you: **write the `WHERE` first, then go back and write the `SET`.**

**Q8. `SELECT *` vs naming columns — why name them?**

Three reasons: you send fewer bytes over the network; adding a column to the table cannot
silently break your code; and an index can only cover a query if the query only needs the
indexed columns.

**Q9. What does `AUTO_INCREMENT` do when a transaction is rolled back?**

**It does not roll back.** Auto-increment is a counter, not a `MAX(id) + 1` lookup. Rolled
back inserts still consume ids. In this project the counter reached 8 while the table was
empty. Do not assume ids are gapless.

---

## 3. Maven

**Q10. What are the three coordinates that uniquely identify a Maven artifact?**

`groupId`, `artifactId`, `version` — the **GAV**. Together they are a globally unique
address: how Maven finds a dependency in a repository, how it stores it in `~/.m2`, and how
two versions of the same library can coexist on one machine.

**Q11. `maven.compiler.release` vs `source` + `target` — why is `release` safer?**

`source`/`target` only control the language level the compiler accepts and the bytecode
version it emits. They do **not** check the API you call. Compile with `-source 8 -target 8`
on JDK 17 and you can still call `List.of()` (a Java 9 method) — it compiles, then dies with
`NoSuchMethodError` on a real Java 8 JVM. `release` (JDK 9+) does all three: language level,
class file version, **and** API signature checking.

**Q12. We declared one dependency but Maven downloaded two. Explain.**

`mysql-connector-j` internally uses protobuf, so Maven read its POM, found its
`<dependencies>`, and added them to our graph. Those are **transitive dependencies**.

Conflict resolution is **nearest-wins**: the version at the shallowest depth from your root
wins; at equal depth, the one declared first. It does **not** pick the newest. Override with
`<dependencyManagement>` or cut a branch with `<exclusions>`. When something behaves
inexplicably, run `mvn dependency:tree` first.

**Q13. Compare dependency scopes `compile`, `test`, `provided`.**

- **`compile`** (default) — available to main and test code, packaged into the output.
  *Example: `mysql-connector-j`.*
- **`test`** — only for compiling and running tests, never packaged. *Example: JUnit 5.*
- **`provided`** — compile against it, do not package it, because the runtime supplies it.
  *Example: `servlet-api` in a war on Tomcat — bundling it causes classloader conflicts.*
- (Bonus: **`runtime`** — not needed to compile, needed to execute.)

---

## 4. OOP and the model

**Q14. `Long id` vs `long id` — why the wrapper?**

`long` defaults to `0`, so "not saved yet" and "id 0" are indistinguishable. `Long` defaults
to `null`, which means exactly "the database has not assigned one". The flip side: unboxing
a `null` `Long` throws `NullPointerException`, which is why `StudentDAOImpl.update()` checks
for `null` before calling `setLong`.

**Q15. Why private fields with public getters and setters instead of public fields?**

1. **An interception point.** A setter is code that runs — a place to enforce invariants,
   normalise, or log. A public field gives you nowhere to stand.
2. **Freedom to change internals.** If `name` is public and you later split it into
   `firstName`/`lastName`, every caller breaks. With `getName()`, you change one method body
   and nobody notices.

The field is an implementation detail; the getter is a **promise**.

**Q16. What is `this`, and why is `this.name = name` necessary?**

`this` is a reference to the object the method was called on. In the constructor, the
parameter `name` **shadows** the field, so the bare word `name` means the parameter. Write
`name = name;` and you assign the parameter to itself — a no-op that compiles with no
warning and leaves the field `null`.

**Q17. Java 17 has `record`. Why not use `record Student(...)`?**

You would gain: private final fields, a canonical constructor, and correct
`equals`/`hashCode`/`toString` for free. You would lose: setters (records are immutable, so
you cannot build one incrementally from a `ResultSet`), and JavaBeans accessor names —
records use `id()`, not `getId()`, which many frameworks require. **Rule of thumb: records
for read-only DTOs, a class when you need mutation or framework compatibility.**

**Q18. This class does not override `equals()` or `hashCode()`. What happens if you put two
`Student` objects with the same `id` into a `HashSet`?**

`Object.equals` is reference identity. `HashSet` uses `hashCode()` to pick a bucket and
`equals()` to compare inside it. Without overrides each object gets a distinct identity
hash, so both land in different buckets and **both survive** — your set holds duplicates,
and `contains(sameStudentLoadedAgain)` returns `false`. Override **both**; `equals` without
`hashCode` is a bug, because equal objects must have equal hash codes.

---

## 5. JDBC

**Q19. What actually happens on the network when `DriverManager.getConnection()` runs?**

A TCP socket is opened, the MySQL handshake runs (protocol version, capability
negotiation), then the authentication exchange — with `caching_sha2_password` that is an
extra round trip, possibly plus fetching the server's public key. Then session state is set.
Several round trips and a server-side thread allocation: **5–50 ms**. At 1,000 requests per
second that exhausts `max_connections` under trivial load. **The replacement is a connection
pool** (HikariCP), which keeps a fixed set of connections open and hands them out.

**Q20. Why is `Class.forName("com.mysql.cj.jdbc.Driver")` no longer needed?**

Since JDBC 4.0 drivers register themselves through `META-INF/services/java.sql.Driver` and
the `ServiceLoader` mechanism. In this project the driver was registered without a single
line of code asking for it. Every tutorial that still shows `Class.forName` is teaching
pre-2006 JDBC.

**Q21. Why does `getResourceAsStream` return `null` instead of throwing when a file is
missing?**

Because a missing *optional* resource is not an error to the JDK — the same API answers
"give me the file if it is there", which is a legitimate question. So absence is signalled
with `null`. The correct handling is to check immediately and throw with a message that says
where the file was expected. Letting the `null` flow into `Properties.load(in)` produces a
bare `NullPointerException` three frames away — **fail-late instead of fail-fast**.

**Q22. Explain precisely why `PreparedStatement` prevents SQL injection but string
concatenation does not.**

Concatenation builds **one string**, so a malicious value becomes part of the *query text*
and the parser reads it as code:

```java
"SELECT * FROM students WHERE name = '" + input + "'"
// input = "x' OR '1'='1"  ->  the WHERE clause is rewritten
```

With a `PreparedStatement` the SQL is sent to the server **first and separately**, and the
server compiles a plan with a hole in it. The value is then sent in a **second, distinct
message as data**, and the server binds it into the pre-compiled plan. **The value never
enters the parser**, so it cannot change the query's structure. The sentence to say: *the
query structure is fixed before the data arrives.*

**Q23. `executeQuery()` vs `executeUpdate()` vs `execute()`?**

- `executeQuery()` → returns a `ResultSet`. For `SELECT`.
- `executeUpdate()` → returns an `int` (rows affected). For `INSERT`, `UPDATE`, `DELETE`, DDL.
- `execute()` → returns `boolean`: `true` if the result is a `ResultSet`, `false` if it is an
  update count. Use it when you **do not know in advance** — dynamic SQL or a stored
  procedure that may return either.

**Q24. Why must you close a `Connection`?**

Each open connection holds a server-side thread and session memory plus a client socket.
Leak enough and MySQL hits `max_connections` and refuses everyone, including you. There is
also a dangling transaction and held locks. try-with-resources guarantees closure because it
compiles to a `finally` block — `close()` runs whether the block completes, throws, or
returns early — and resources close in **reverse declaration order**, which is the only
correct order.

**Q25. What is `SQLState` vs `getErrorCode()`?**

`getSQLState()` is the **standardised, portable** five-character code from the SQL standard
(`23000` = integrity constraint violation, `42S02` = table not found, `28000` = invalid
authorization). `getErrorCode()` is the **vendor-specific** number (`1062`, `1146`, `1045`).
Catch on the class or SQLState; log the vendor code.

---

## 6. DAO pattern

**Q26. Why does `StudentDAO` declare `throws SQLException` rather than catching it and
returning a default?**

Because the DAO has no idea what a failure means to the application. Swallowing it turns a
real failure into a **lie**:

```java
catch (SQLException e) { return Optional.empty(); }  // "no student" — or the DB is on fire
```

Now the caller cannot tell "no such student" from "I could not ask". And the DAO cannot
choose policy — retry? show a message? roll back? That is an application decision. So the
DAO does the honest thing and propagates.

**Q27. Why does `findById` return `Optional<Student>` rather than `null`?**

`Optional` makes "not found" **part of the type**, so the compiler forces you to deal with
it. It is self-documenting, and it is impossible to use accidentally as a value — there is no
way to extract a `Student` without writing `orElse`, `orElseThrow`, or `isPresent`.

**Where not to use it:** as a **field type** (not `Serializable`, extra allocation) and as a
**method parameter** (just overload). `Optional` is for **return types only**.

**Q28. Why is `mapRow` private?**

It exposes a `ResultSet` — a JDBC type. Make it public and callers start depending on JDBC,
so `StudentDAOImpl` could never be swapped for a non-JDBC implementation and the point of
the interface evaporates. The DAO's job is to **hide** JDBC; a public method returning a
`ResultSet` leaks the exact thing you are hiding. That is a **leaky abstraction**.

**Q29. `update` returns `boolean`, `insert` returns `long`. Justify both.**

`update` returns `boolean` because the only question is *"did a row change?"* —
`UPDATE ... WHERE id = 999999` affects **zero rows and succeeds**. Return `void` and you
would report success for a student that does not exist. `insert` returns `long` because a
new row needs **its identity**. Return the information the caller needs, and no more.

**Q30. You need "find all students in a given course". What are the exact edits?**

1. **`StudentDAO.java`** — add `List<Student> findByCourse(String course) throws SQLException;`
2. **`StudentDAOImpl.java`** — add the SQL constant and the method that binds and reuses
   `mapRow`.
3. **`StudentService.java`** — add `findStudentsByCourse(String)` that strips, validates
   non-blank, and delegates.

Then, and only then, `Main` gets a menu entry. **That order is the dependency direction made
visible** — contract first, implementation second, policy third, UI last.

---

## 7. Service layer

**Q31. Why should business logic not live in the DAO?**

- **Different reasons to change.** A DAO changes for storage reasons; a service changes for
  business reasons. Two things that change for different reasons belong in different classes.
- **A DAO cannot see the whole picture.** "One email, one student" needs a query spanning
  the current row and the table; "max 3 courses" needs two tables. Such rules turn a DAO
  from a data mover into an application.
- **A DAO cannot decide policy.** On `SQLException`, should the app crash, retry, or
  apologise? The DAO does not know if it was called from a console menu or a batch job.
- **The UI must be replaceable.** If the rule lives in `Main`, a REST API has to reimplement
  it.
- **Testability.** The service depends on the interface, so it can be tested with an
  in-memory fake and no database.

**Q32. Why must `cleanUp` run before `validate`?**

Because normalisation changes what validation sees. `"   "` (three spaces) fails a blank
check either way, but `"  ravi@x.com  "` **passes** an email-shape check that would reject
it after stripping — or worse, is stored with the spaces and then never matches a lookup.
Order is a design decision, not style.

**Q33. `findById` in the DAO returns `Optional`, but `findStudent` in the service returns
`Student` and throws. Why represent the same fact two ways?**

Because the layers answer different questions. The DAO reports a **fact**: "there may or may
not be a row." The service applies **policy**: "in this application, looking up a missing
student is an error." Pushing the policy down into the DAO would make the DAO opinionated;
pushing the fact up into the UI would force every caller to handle `Optional` and probably
forget to.

**Q34. Why is `studentDAO` declared `private final` and injected through the constructor
instead of `new StudentDAOImpl()` inside the service?**

`final` means the dependency is fixed at construction, which makes the service immutable and
safe to share between threads. Injection means the service never chooses MySQL — so a test
can hand it an in-memory implementation. If the service called `new StudentDAOImpl()`
itself, the age rule could not be tested without a live database. **That single fact is the
entire justification for dependency injection, and for Spring.**

**Q35. What breaks if you write `existing.get().getId() == excludeId` instead of
`.equals(excludeId)`?**

`==` compares **references** for objects. `Long` is an object. For values in the `Long`
cache range (−128 to 127) the JVM reuses instances, so `==` **appears to work** — and then
fails silently for id 200. The self-duplicate guard stops working and a student can no longer
save their own record. **This is the classic Java wrapper bug.**

---

## 8. Exceptions

**Q36. Checked or unchecked — how do you decide?**

Use a **checked** exception when the immediate caller can **meaningfully recover** and you
want to force them to think about it. Use an **unchecked** one when the failure is a
programming error or something only a higher layer can handle. The service's
`ValidationException` is unchecked because the service cannot repair a bad email — only the
user can — so a `throws` clause on every method would produce `catch` blocks nobody can act
on.

**Q37. The DAO uses checked `SQLException` but the service wraps it in unchecked
`DataAccessException`. Is that inconsistent?**

No — it is **exception translation**, and it is the standard pattern (Spring does exactly
this). `SQLException` is checked because it is JDBC's signature and we do not get to choose
it. We convert it at our boundary because no layer above should have to import `java.sql` —
if the UI did, swapping MySQL would break the menu. The original exception is kept as the
**cause**, so `getCause()` still returns the real `SQLException` with its SQLState and error
code. **Nothing is lost.**

**Q38. Why is `DuplicateEmailException` not a subclass of `ValidationException`?**

Because the value is not malformed — it is perfectly well formed and simply **taken**. That
is a conflict with existing data, not a bad input. In HTTP terms, validation is 400 Bad
Request and a duplicate is 409 Conflict. Keeping them separate lets the UI re-prompt for
just the email rather than abort the whole operation.

**Q39. Where should exceptions be caught?**

At a **boundary** — a place where you can actually do something: the console menu loop, a
REST controller advice, a message consumer. Not at every call site. In this project there is
exactly one catch block, in `Main.handleMenuChoice`, and it separates two categories:

- the user's problem → print the message and continue
- our problem → apologise, and keep the technical detail for the log

**Q40. What is the most common mistake with try-with-resources?**

Assuming you need `close()` in a `finally` block. You do not — try-with-resources handles
it, including on exceptions and early returns, and closes in reverse declaration order. The
second most common mistake is declaring the resources in the wrong order, or putting
`ps.executeQuery()` anywhere other than last.

---

# Part 2 — The project explained

## 1. Complete architecture

Four layers, dependencies pointing one way.

```
Main ──► StudentService ──► StudentDAO (interface) ◄── StudentDAOImpl ──► DatabaseConnection ──► MySQL
                                        ▲
                              model.Student (shared by all)
```

- **`Main`** — console UI and **composition root**. Reads input, prints output, holds no
  rules and no SQL. Contains the only `new StudentDAOImpl()` in the codebase.
- **`StudentService`** — business rules, validation, normalisation, and the application's
  exception policy.
- **`StudentDAO` / `StudentDAOImpl`** — the contract and the only class that knows SQL
  exists.
- **`DatabaseConnection`** — reads `db.properties` from the classpath and hands out JDBC
  connections.
- **`model.Student`** — a plain data holder that belongs to no layer, which is why it lives
  in its own package and imports nothing.
- **`exception`** — four unchecked exceptions that express *why* something failed.

The test of a good layering: **can you name the one file you would change for each kind of
change?** Business rule → `StudentService`. SQL → `StudentDAOImpl`. Menu → `Main`. Schema →
`db/schema.sql`. Configuration → `db.properties`. If any answer is "two or three files",
the layering has leaked.

## 2. Request flow

```
User types 1
  Main.addStudent()
    reads five lines, builds new Student(...)
  StudentService.addStudent(student)
    cleanUp()                strip + lower-case
    validate()               blanks, email shape, phone digits, age 15-100
    requireEmailAvailable()  studentDAO.findByEmail()  ── one SELECT
    studentDAO.insert()      ── one INSERT
  StudentDAOImpl.insert()
    DatabaseConnection.getConnection()
    prepareStatement(INSERT_SQL, RETURN_GENERATED_KEYS)
    five setX() calls
    executeUpdate()
    getGeneratedKeys() -> long
  Main prints "Saved. The database assigned id 15"
```

**Two database round trips per add** (the uniqueness check, then the insert). Worth knowing:
it is possible to drop the pre-check and rely on the constraint alone, at the cost of a
less friendly error. The trade-off is deliberate.

## 3. Database flow

- **Connections** are created per operation and closed by try-with-resources. No pooling —
  deliberate, and the first thing to change for production.
- **Transactions** are implicit. Every statement autocommits. `StudentService.addStudent`
  touches the database twice, so in theory another request could claim the email between the
  check and the insert — which is exactly why the `UNIQUE` constraint exists as the real
  guarantee. **Wrapping both statements in one transaction would narrow the window but not
  close it** (the constraint is still the only true defence). Knowing that distinction is
  what separates a good answer from a great one.
- **Generated keys** come back via `Statement.RETURN_GENERATED_KEYS` and
  `getGeneratedKeys()`.
- **Timestamps** are maintained entirely by MySQL (`DEFAULT CURRENT_TIMESTAMP` and
  `ON UPDATE CURRENT_TIMESTAMP`), so no Java code can forget them.

## 4. Authentication flow

**There is none, and that is a deliberate scope decision.** This project has no login, no
sessions, and no authorisation. Say that clearly rather than pretending otherwise.

If asked what it *would* look like:

1. A `users` table with a **salted, hashed** password (`BCrypt`, never MD5 or SHA-1, never
   plaintext).
2. An `AuthenticationService` that verifies credentials and issues a token (JWT) or starts a
   server-side session.
3. A filter/interceptor that validates the token on every request and populates a security
   context.
4. **Authorisation** — roles (`ADMIN` can delete, `STAFF` can only read), checked in the
   service layer, because authorisation is a business rule and belongs with the other ones.
5. **The layered design already accommodates it**: a new service method and a filter in
   front of the UI. No existing class needs to be restructured — which is the point of
   having layers.

## 5. Important business logic

All of it lives in `StudentService`:

| Rule | Where | Why not in the DAO |
|------|-------|--------------------|
| Strip whitespace, lower-case email | `cleanUp()` | It is a policy about input, not about storage |
| Name/email/phone/course not blank | `requireText()` | Business vocabulary |
| Email must look like an email | `requireEmailShape()` | Business rule, likely to change |
| Phone must be 10–15 digits | `requirePhoneShape()` | Business rule |
| Age between 15 and 100 | `validate()` | Business rule, mirrors the DB `CHECK` |
| One email, one student | `requireEmailAvailable()` | Needs a *query*, and needs the `excludeId` exception for updates |
| Not found is an error | `findStudent()` | Policy, not a fact |
| Translate `SQLException` | every method | Only the service knows what failure means here |

## 6. Most difficult parts

Be honest about these — interviewers trust candidates who name real difficulties.

1. **The self-duplicate bug on update.** When a student saves without changing their email,
   the uniqueness check finds **themselves** and rejects the update. The fix is the
   `excludeId` parameter. It is easy to write, easy to forget, and it ships to production
   constantly.
2. **Translating `SQLException` without losing information.** You must decide *where* to
   translate and remember to preserve the cause, or you lose the SQLState and error code you
   will need in the logs.
3. **Getting the parameter order right.** `UPDATE` has six `?` and the compiler cannot help
   you. Swapping two `setString` calls compiles cleanly and corrupts data.
4. **Choosing `Long` over `long` and then dealing with the consequences** — every read of
   `getId()` is now nullable, and `setLong(6, student.getId())` would throw a
   `NullPointerException` if you did not guard it.
5. **`Optional` at the right layer.** It is right at the DAO and wrong at the service,
   because the two layers answer different questions.

## 7. Common bugs

| Bug | Symptom | Fix |
|-----|---------|-----|
| Missing `WHERE` on `UPDATE`/`DELETE` | Every row changed or deleted | Write `WHERE` first |
| Wrong `?` index in `setX` | Wrong column updated, silently | Number the parameters; keep SQL in constants |
| `Scanner.nextInt()` then `nextLine()` | A question appears to be skipped | Read lines and parse them yourself |
| `==` on `Long` | Works for ids 1–127, fails after | Always `.equals()` for wrappers |
| Unboxing a `null` `Long` | `NullPointerException` at `setLong` | Guard for `null` before unboxing |
| Missing `RETURN_GENERATED_KEYS` | `getGeneratedKeys()` returns nothing | Pass the flag to `prepareStatement` |
| `ResultSet` read before `next()` | `SQLException: Before start of result set` | Always `next()` first |
| 1-based column index | Off-by-one | Prefer column names over indexes |
| `toLowerCase()` with no locale | Different behaviour on Turkish machines | `toLowerCase(Locale.ROOT)` |
| `trim()` vs `strip()` | Non-breaking spaces survive | Use `strip()` (Java 11+) |
| Swallowing `SQLException` | "No students found" when the DB is down | Let it propagate; translate at a boundary |
| `catch (Exception e) {}` | Silent failure, hours of debugging | Never catch what you cannot handle |
| Leaking connections | `Too many connections` under load | try-with-resources, always |
| `LIKE '%x%'` on a large table | Full table scan | `FULLTEXT` index or a search engine |

## 8. Why each technology was chosen

- **Java 17 (LTS)** — `record`, arrow `switch`, `String.strip()`, `var`. LTS means long-term
  security patches, which is what you deploy.
- **Maven** — declarative dependencies, transitive resolution, the standard directory
  layout, and a build lifecycle every Java developer already knows.
- **JDBC** — chosen precisely *because* it is verbose. Every ORM hides the SQL; here the SQL
  is the point.
- **MySQL 8** — free, ubiquitous, and it has real `CHECK` constraints (8.0.16+), which let
  this project demonstrate the database defending itself.
- **No Spring** — it would have replaced the two most interesting lines of the project
  (constructor injection and the composition root) with annotations, and hidden the
  exception translation we wanted to learn.

## 9. Alternatives

| Instead of | Alternative | Trade-off |
|-----------|-------------|-----------|
| JDBC | Hibernate / JPA | No boilerplate, but the SQL becomes invisible and lazy-loading becomes a new class of bug |
| JDBC | jOOQ / MyBatis | SQL stays explicit with less boilerplate; another dependency to learn |
| `DriverManager` | HikariCP | Essential for production; hides the fundamentals while learning |
| Hand-written SQL | Flyway / Liquibase | Versioned, repeatable schema changes; more tooling |
| `StudentService` | Spring `@Service` | Automatic wiring, transactions, AOP; magic you have to trust |
| `Main` console | Spring Boot REST | The same service and DAO layers, plus HTTP, JSON and validation annotations |
| `VARCHAR` course | A `courses` table + FK | True 3NF, referential integrity; a `JOIN` on every read |
| Stored `age` | `date_of_birth` + computation | Always correct; index-unfriendly queries unless you compute the range |
| Custom `equals` | `record` | Free and correct; loses mutability and `getX()` accessors |
| Manual validation | Jakarta Bean Validation | Declarative and standard; a framework dependency |

## 10. How to explain this project in a real interview

Use this structure. Roughly two minutes, then let them dig.

> **The problem.** A console CRUD application for student records, built with plain JDBC —
> deliberately without Spring or an ORM, because I wanted to understand what those
> frameworks actually do for me.
>
> **The architecture.** Four layers. `Main` is the console UI and the composition root.
> `StudentService` holds all the business rules. `StudentDAO` is an interface and
> `StudentDAOImpl` is the only class that knows SQL exists. `DatabaseConnection` reads
> credentials from a properties file on the classpath. Dependencies point one way, and
> `Student` is the object that travels through all of them.
>
> **The interesting decisions.** I used `Long` for the id rather than `long`, because a
> student that has not been saved yet has no id and `0` would be indistinguishable from a
> real one. I kept `email` unique in the database *and* checked it in the service — the Java
> check gives a friendly message, the database constraint is the only thing that is atomic
> under concurrent requests. And I deliberately denormalised `course` into a column rather
> than a separate table, because at this size the join costs more than it buys.
>
> **The hardest part** was the uniqueness check on update: without excluding the student
> being edited, saving without changing your email fails because the check finds yourself.
>
> **What I would do next** is add connection pooling — `DriverManager` opens a new TCP
> connection and does a full auth handshake on every call, which is fine here and fatal
> under load. Because connection creation is behind one class, that is a change to a single
> file.

Then **stop talking**. Let them ask. The follow-up questions are where you win.

---

# Part 3 — Mock interview

Answer these **out loud, without looking at the code**. Then check yourself against Part 1
and Part 2.

## Round 1 — Design (5 minutes)

1. Walk me through your architecture. Why four layers and not two?
2. Why does `Student` live in its own package instead of inside `model` of the DAO or the
   service?
3. Your `StudentService` calls `findByEmail` and then `insert`. Is that a race condition?
   What protects you?
4. Why does the service throw exceptions instead of returning error codes or `boolean`s?
5. If I asked you to add a `Course` entity with a proper foreign key, what files change and
   in what order?

## Round 2 — Code reading (5 minutes)

6. Here is `StudentDAOImpl.update()`. Point at every line that would fail if `Student.id`
   were `long` instead of `Long`.
7. Why does `mapRow` exist, and why is it private?
8. What happens if I swap `ps.setString(2, ...)` and `ps.setString(3, ...)` in `update`?
   Would the compiler tell you?
9. Why is there exactly one `try`/`catch` in `Main` and none anywhere else in the UI?
10. `Main.readInt` reads a line and parses it. Why not just call `Scanner.nextInt()`?

## Round 3 — Troubleshooting (5 minutes)

11. A user reports "it says my email is already taken, but it is my own email and I did not
    change it." Where do you look first?
12. The app prints "No students found" but there are rows in the table. What are the three
    most likely causes?
13. After a week, the app starts throwing `Too many connections`. What happened?
14. `mvn compile` fails with `NoSuchMethodError` on `List.of()`. What is the cause?
15. Two students are added with the same email within the same millisecond and both
    succeed. How is that possible, and which layer failed?

## Round 4 — Trade-offs (5 minutes)

16. Why not just use Hibernate and delete 300 lines of your code?
17. Why is `course` not in its own table?
18. Why is `age` a column instead of a computed value?
19. Your `search` uses `LIKE '%x%'`. What happens at ten million rows?
20. Where would you put authentication and authorisation in this architecture, and why
    there?

## Round 5 — The hard one

21. **Explain this entire project to me in three minutes without looking at any code.**
    Cover: the problem, the architecture, two decisions you would defend, the hardest part,
    and what you would do next.

If you can do question 21 cleanly, you understand the project. That is the bar.
