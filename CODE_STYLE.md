# Code Style

This is the sole style guide for code in this repository. It governs
design, structure, naming intent, and test design. For anything it
doesn't address (exact indentation, brace placement, import ordering,
etc.), use ordinary, conventional Java formatting and judgment — there is
no second style guide to reconcile against, and no auto-formatter is
wired into the build. Rigid, tool-enforced formatting rules are
deliberately avoided here in favor of engineering judgment.

`qa-ticket`'s Detroit-school testing rules (real collaborators by
default, mocking only at genuine external boundaries) and `CLAUDE.md`'s
testing philosophy section take precedence over anything below that might
read as narrower — treat them as the same philosophy, not competing ones.

---

# A. General Design Principles

## 1. Immutability

### Core Guidelines

- Don't provide methods that modify the class state (e.g mutators)
- Make the class **final**
- Make all fields **final**
- Make all fields **private**
- Ensure exclusive access to any mutable components
	- For **Collections**, return *Defensive Copies*

### Advantages

If you can design a class to be immutable, you should highly prefer that for the following reasons:
- Simplicity
- **Thread-safety**; they require no need for synchronisation
- Even their internals can be shared
- Immutable Objects are great building blocks for other Objects
- Atomicity Failure for free; state never changes so there is no chance of inconsistent state.

### Disadvantages

Immutable objects require a new object for each distinct value. As a result, if you want to perform a multistep calculation, you only need to keep the last reference of the immutable object with the last state.

This sacrifices performance and renders all previous object creation unnecessary.

**How to fix**:
- Guess the **multistep operations** of the class and provide them as **primitives**

## 2. Enumerations

- For Domain modelling of a concept that has a *predefined* set of values, favour enums instead of primitives.
- **Strategy Enum Pattern**
	- When an enum behaviour is *required* for each enum value, do not switch on enum values to provide the behaviour.
	- Instead, provide the behaviour as a **required** type that matches the enum value
	- **Why**:
		- There is maintenance danger when adding a new enum value to forget adding the behaviour if you switch
		- Instead, the type enforces you to add the strategy when adding a new enum value
- **Do not Depend on Ordinal**
	- Use a **EnumMap** or **EnumSet** when you need a enum and hashing
	- Do not map enum values to **database** using ordinal

## 3. Nullability

**Rule of Thumb**

Nulls are allowed **ONLY** at boundaries.

**Design**

When designing an API (public, class, programming, any)

- Assume a parameter of a method is **implicitly not null**
- If nullable, make the field `Optional` *explicitly*

**Why**: It is much easier for the reader to treat nullable fields as the exception instead of the rule.

**Boundaries**

The system boundaries **allow null values** so you need to handle them appropriately.
In such layers, the system interfaces with the outside world (UI, DB) and nullability has to be handled there.

- Reject (fail fast)
- Apply default

**Examples**

- Application Layer (e.g Controllers)
- Infrastructure Layer (e.g Repositories, DAOs)

 **Return Types**

- **Optional** instead of `null`
- **Collections**: return empty collection instead of `null`
- **Null Object Pattern**: when you have design control over the object type
		- [link](https://refactoring.guru/introduce-null-object)
- **Primitives**: prefer them over their respective Box Types
	- `boolean` instead of `Boolean`
	- `int` instead of `Integer`
	- `long` instead of `Long`
	- `double` instead of `Double`

## 4. Exceptions

- **Don't ignore Typed exceptions**
	- Log
	- Handle
	- Re-throw
- Do not use Exceptions for **normal** flow control; Only design them for exceptional cases
- Avoid **unnecessary** use of checked exceptions.
- **Standard Exceptions**
	- `IllegalArgumentException` - use when the input arguments have wrong value
	- `IllegalStateException` - used when the state of the object is wrong e.g premature invocation on an uninitialised object
	- `NullPointerException` - Do not handle it directly if needed; use utility libraries such as `Objects.requireNonNull`
	- `UnsupportedOperationException` - Example of usage can be for subclasses that conform to a public contract but don't support the function (though this breaks the Open-Closed principle)
- **Exception Translation**
	- Higher layers should catch lower-level exceptions and, in their place, throw exceptions that can be explained in terms of the higher-level abstraction
- **Result Pattern**
	- When designing an API, you can consider using the [Result Pattern](https://medium.com/@dev-hancock/the-result-pattern-simplifying-error-handling-in-your-code-fc31bb50a244)
		- **Caution**: When dealing with Spring's `@Transactional` or Frameworks, Tools that **depend** on exceptions, result pattern might *interfere* with the behaviour of these tools that rely on exceptions (see [here](https://krzysztof-owczarek.medium.com/using-the-result-pattern-in-kotlin-or-java-may-break-your-spring-transactional-guarantees-5cbfac6e719d))
- **Exception Handling**
	- **Fail Fast**
	- Handle **Globally** at the **Boundary**

## 5. Nesting

- Code is allowed to be **3 levels deep** max
- **Reduce** Nesting By
	- Use **early returns** for guard clauses
	- Use **ternary operators** with simple conditions as *readable* one-liners
	- Extracting into separate functions
	- Consider **Maps** instead of conditionals
	- Use **polymorphism** with caution to avoid conditionals

## 6. Functions Design

- Use a function when there is a distance between your intention and *how* the intention will be implemented.

# Β. Naming Conventions

## 1. General Guidelines

- Use *naming* to your advantage to express business logic with **clarity**
- **Encapsulate** logic where it belongs
	- Do not leave code *scattered* around
	- Everything has its place
- **Comment** to explain *why* not how
- **Refactor** continuously

## 2. Blacklist

- Abbreviations
	- **Example**: `pixelHeight` over `pxHght`
- Types in Variable Names
- Types in your types (e.g `Abstract`, `Base`)

## 3. Whitelist

- Patterns are Allowed In Names
	- **Examples**
		- `FootTemplate`
		- `PersonFactory`
- Express Units in Types First, Name Otherwise
	- `TimeUnit delay` type is superior to `Integer delaySeconds`

# C. Object Creation & Destruction

## 1. Consider Static Factory Methods Instead of Constructors

- Encapsulate validation logic of creation in static factory methods
- Keep constructor simple and private
	- No validation logic in constructors
	- All-args constructor is reused by static factory methods

- **Advantages**:
	- Static factory methods have names unlike constructors
		- Clear intent; increased readability
		- **Return Object Control**
			- Cache (they don't need to return a new object each time)
			- They can return an object of any subtype of their return type
			- Backwards Compatibility Flexibility
				- The class of the return type can vary (e.g in a new release, the return object is changed to a subclass)
			- The class of the returned Object does not need to exist when the factory method is written (e.g the method returns a List; you can create a new list concretion in the future)
- **Disadvantages**
	- Classes without public/protected constructors that use only factory methods cannot be subclassed; (blessing in disguise to favour composition over inheritance)
	- Harder to find by programmers
		- Document
		- Use common naming practices

### Naming Conventions

| Value         | Case                                                                                                                  | Example                           | When to Use                                                                                                                                                     |
| ------------- | --------------------------------------------------------------------------------------------------------------------- | --------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `from`        | Type Conversion from Another Type<br><br>Enumerations                                                                 | ```Instant.from(zonedDateTime)``` | ✔ You are adapting an external type<br><br>✔ There is a conceptual “source → target” transformation<br><br>✔ You want to express _conversion_, not construction |
| `of`          | Aggregate or concise factory                                                                                          | `Duration.ofSeconds(10);`         | Creates an instance **from its components**                                                                                                                     |
| `valueOf`     | Canonical conversion to a value type.<br><br>Used rarely with single arguments where the argument is already a value. | `Integer.valueOf(10)`             | Returns an instance **representing the same value** as the argument                                                                                             |
| `getInstance` | Instance is not assumed to be a new Object                                                                            | Singletons<br>Cached Objects      | ✔ Callers should **not assume new object**<br><br>✔ Object lifecycle is managed elsewhere<br><br>❌ Discouraged in Domain Code                                   |
| `create`      | `Factory`                                                                                                             | `Foo a = fooFactory.create();`    | Construct a **new instance**                                                                                                                                    |

## 2. Consider a Simple Factory

- The cardinality of the dependencies for creation is high
- The creation logic falls outside the scope of your class

## 3. Consider a Builder

- High Parameter Cardinality
- Encapsulate complex initialisation logic
- Builders can be used to describe multiple steps *fluently*

## 4. String Creation

**Use**
```
String name = "George"
```
**Instead of**
```
String name = new String("George");
```

# D. Common Design Cases

## 1. Data Transfer Objects (DTO)

DTOs can be divided into two main categories: *external* and *internal*.
The distinction is based on who has the **schema ownership**.

#### 1. External

- `DTO` that interface with the **outside** world and external systems
- They are usually **Serialisable** (`JSON`, `XML` etc.)
- They **do not** contain any other logic besides state and accessors / mutators.
- **Nullable** Fields are allowed
- **Examples**
	- Public Contracts
		- Messages
	- **REST API** Objects
		- **Request**
		- **Response**

#### 2. Internal

- DTOs that do not interface with the outside world
- They *can* contain **nullable** fields depending on the modelling
- **Examples**
	- A **Request** Object to a Service
	- A **Response** Object of a Service
	- **Context** classes
		- Commands
		- Client Invocation

#### 3. Mapping

Often times, there is a need to map between DTO and Domain Objects in Application or Infrastructure Layers.

Place the mappers in these respective layers. Keep the domain agnostic of mapping.

**Naming Convention**

- `fromDto`
- `toDto`

## 2. Utility Classes

- Utility classes are highly discouraged because they encourage **poor modelling**.
- If you need a utility class, chances are the behaviour **can be encapsulated better**.
- **Examples**
	- Aggregate Operations
	- Test Helper Methods
- **General Design Guidelines**:
	- a. First Check if the operation can be *encapsulated* in a **domain object**
		- A method for an **enum** might fit better in the enum **itself**
		- A `File` method might fit to the `File` instead of `FileUtil`
	- b. If the method does not fit in the **domain object**, consider using **static factory methods**
		- On the class itself (e.g `File`)
		- On the respective aggregate operations class e.g `Files` is a good example with helper **static methods**
		- `Collections` provides static factory methods
	- **Naming Convention**: **Prefer plural** for aggregate operations instead of `util` suffix

# E. Design Patterns

- Standard design patterns are encouraged to be used when the modelling case is common and there is a fit
- Do not use them if they don't match your use case
- **Common Creational Patterns** used 90% of the time
	- Factory methods & Simple Factories
	- Builders
- **Benefits**:
	- Common Language across engineers
	- High Readability
	- Less unexpected concepts
- [Refactoring Guru](https://refactoring.guru/design-patterns) is a great source for explaining standard patterns in a visually appealing way

# F. Test Design

## 1. Goal

- To develop tests that depend on **stable** concepts that won't change
- If the implementation details *change*, the tests should not be affected
- **Developer Happiness**
	- Your tests should make your life easier, not more difficult
	- You should be able to run them **easy** & **fast** as you make changes
- Runner Independent Tests
	- Should pass with any runner e.g maven, **IDE** etc

## 2. Unit Testing

### 1. Goal

- The Test Suite should be
	- **Fast**
	- **Repeatable**
	- **Deterministic**
	- **Simple**
- I should be able to run tests **easy**
	- Right-click -> Run Tests from my **IDE**
- The tests test **One Thing**

### 2. Best Practices

- Prefer `Classicist` over `Mockist` testing style
- Test against **Interfaces**, not **Details**
- Test Behaviours that won't change
- Test zero, one, two or more cases
	- Big size input should be moved to Load testing, not unit testing
- **Test Isolation**
	- Order Agnosticism
	- Data Ownership
		- Setup
		- Assert
		- Clean

### 3. Banned Practices

- **No loops** in test assertions
	- They make tests more complex than they should be
	- Allowed Domain Assertion internals
- **Do not** add configuration to the **build lifecycle** to configure your tests
	- They should be executed in isolation
	- `Test-containers` is a good example for running them simply instead of using `Arquillian` through the maven build lifecycle
- **Avoid** blind `Thread.sleep`

## 3. Setup Logic

- You can use **fluent builders** to encapsulate the setup logic
	- The logic obtains a **name**
		- Increased readability
		- Intent
- Use **Test Factories** to facilitate the creation of **Test Data**

## 4. Assertions

### 1. Domain Assertions

Use the [Assertion Object Pattern](https://refactoring.guru/introduce-assertion) to encapsulate the domain expectations with an assertion style.

**Example**:

Given a `Person` class that we want to test for their name, if their active or their age.
We can encapsulate aggregate assertions in a `PersonAssertions` class.

These assertions must have business meaning and may be comprised by simpler assertions e.g `isActive()`.

**Benefit**: The domain language expectations is expressed via the tests

```java
PersonAssertions.assertThat(person)
    .hasName("John")
    .isActive()
    .hasAge(30);
```

### 2. Soft Assertions

Soft assertions differ from hard assertions by not failing-fast at the line where the assertion failed. They continue to the next assertions.

Soft assertions might be useful when asserting on:

- Collections
- DTOs
- Projections
- Mappings
- Domain assertions of **one** concept

### 3. AssertJ

- Favour [`AssertJ`](https://assertj.github.io/doc/) over [`JUnit`](https://junit.org/) **Assertions**.
- It provides a fluent explicit API for expressing precisely assertions without mixing *actual* from **expected** values.

## 5. Loose Coupling

- The test suite **varies independently** of the Application as much as possible
- Treat the application as a **blackbox** using containers
- Consider **Publish-Subscribe** for testing async workflows
	- Consider a **test mode** profile
- Couple to Contracts
	- REST Endpoints
	- Domain Services & Objects that won't change

# References

- **Books**
	- [**Effective Java** by Joshua Blosch](https://kea.nu/files/textbooks/new/Effective%20Java%20%282017%2C%20Addison-Wesley%29.pdf)
- **Articles**
	- [Understanding Object Callisthenics: Writing Cleaner Code](https://dev.to/muzammilnm/understanding-object-calisthenics-writing-cleaner-code-maj)
- **Videos**
	- **CodeAesthetic**
		- [Why You Shouldn't Nest Your Code](https://www.youtube.com/watch?v=CFRhGnuXG-4)
		- [Naming Things in Code](https://www.youtube.com/watch?v=-J3wNP6u5YU)
	- **Martin Flower**
		- [What's the Right Function Length](https://www.youtube.com/watch?v=hPPFri2zRfw)
