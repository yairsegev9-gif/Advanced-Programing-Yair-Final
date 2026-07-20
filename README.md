# Advanced Programming Final Project

## 1. Project Background

This project implements the six-part Advanced Programming assignment. It begins with a publish/subscribe messaging model and develops into a configurable computational graph served through a custom HTTP server and browser-based interface.

Messages are published to topics, agents subscribe to topics, and agents publish computed results to other topics. A configuration file defines the graph dynamically, allowing the same application to execute different computational flows.

## 2. Architecture and Design

The implementation is divided into the following layers:

- Model: `Message`, `Topic`, `Agent`, `TopicManagerSingleton`
- Concurrency: `ParallelAgent`
- Configuration: `Config`, `GenericConfig`, `PlusAgent`, `IncAgent`, `BinOpAgent`
- Computational graph: `Node`, `Graph`
- HTTP server: `RequestParser`, `HTTPServer`, `MyHTTPServer`
- Web application: `Servlet`, `ConfLoader`, `TopicDisplayer`, `HtmlLoader`
- View: `HtmlGraphWriter` and static HTML files

The HTTP server uses longest-prefix URI matching, a bounded worker pool, and explicit shutdown.

Configuration loading uses reflection and agent constructors of the form:

    (String[] subs, String[] pubs)

## 3. Package Structure

- `src/graph` - messaging model, topics, agents, and `ParallelAgent`
- `src/configs` - configuration classes and computational graph classes
- `src/server` - HTTP request parser and server implementation
- `src/servlets` - servlet interface and web application servlets
- `src/views` - graph HTML generation
- `src/test` - dependency-free regression tests
- `config_files` - uploaded and example configuration files
- `html_files` - static browser interface
- `docs/javadoc` - generated API documentation
- `submission_artifacts` - submission ZIP files, the final link file, and the demo configuration file

## 4. Java Version

Java 17.

## 5. Installation

No external dependencies are required.

Clone or download the repository, make sure Java 17 is installed, and open the project in IntelliJ or compile it using `javac`.

## 6. IntelliJ Instructions

1. Open the repository folder in IntelliJ.
2. Mark `src` as the source root if necessary.
3. Select a Java 17 SDK.
4. Run `test.AllTests` to verify the project.
5. Run `Main` to start the web application.

## 7. Compile Command

Run the following command from the project root:

    javac --release 17 -d out src\*.java src\graph\*.java src\configs\*.java src\server\*.java src\servlets\*.java src\views\*.java src\test\*.java

## 8. Test Command

    java -cp out test.AllTests

Expected output:

    CoreGraphTest passed
    GraphTest passed
    ConfigTest passed
    HTTPServerTest passed
    WebAppTest passed
    AllTests passed

## 9. Run Command

    java -cp out Main

Then open:

    http://localhost:8080/app/index.html

Press Enter in the terminal to stop the server gracefully.

## 10. Example Configuration

    configs.PlusAgent
    A,B
    C
    configs.IncAgent
    C
    D

This configuration computes:

    C = A + B
    D = C + 1

## 11. Main Flows

### Configuration Upload

The browser uploads a configuration file to `/upload` using `multipart/form-data`.

`ConfLoader` saves the uploaded file, loads it using `GenericConfig`, creates the corresponding computational graph, and returns the generated graph HTML.

For compatibility with direct tests and older clients, `ConfLoader` also accepts URL-encoded or raw configuration text.

### Graph Construction

`Graph.createFromTopics()` reads the current topics and subscribed agents from `TopicManagerSingleton`.

Topics are represented as rectangular nodes with names beginning with `T:`, while agents are represented as circular nodes with names beginning with `A:`.

Directed arrows show the message flow between topics and agents.

### Topic Publication

The browser sends a request such as:

    /publish?topic=A&message=5

`TopicDisplayer` publishes the message and returns an HTML table containing the latest value of every topic.

### Dynamic HTML Graph

`HtmlGraphWriter` converts a `Graph` instance into SVG-based HTML.

Topics are displayed as rectangles, agents as circles, and directed edges represent the computational flow.

## 12. HTTP Server Reuse Example

    HTTPServer server = new MyHTTPServer(8080, 5);

    server.addServlet("GET", "/app/", new HtmlLoader("html_files"));
    server.addServlet("GET", "/publish", new TopicDisplayer());

    server.start();

    // Later:
    server.close();

The server can be reused with custom `Servlet` implementations. Each servlet receives a parsed `RequestInfo` object and writes a complete HTTP response to the provided output stream.

## 13. Known Assumptions

- The integrated project uses the packages `graph`, `configs`, `server`, `servlets`, and `views`.
- Separate Exercise 1–5 submission copies are stored under `submission_artifacts` with `package test;`.
- `Node.message` is stored as `byte[]`.
- Graph node names use `T:<topic>` and `A:<agent>`, so the first character identifies the node type.
- The browser uploads configuration files using `multipart/form-data`.
- `ConfLoader` also accepts URL-encoded or raw configuration text for compatibility.
- Uploaded filenames are sanitized before being written to the server.

## 14. Javadoc

Generated Javadoc is available at:

    docs/javadoc/index.html

To regenerate it:

    javadoc -d docs\javadoc -sourcepath src src\graph\*.java src\configs\*.java src\server\*.java src\servlets\*.java src\views\*.java

## 15. Demo Video

Video URL:

    Video link will be added before the final submission.

## 16. Submitter

- Name: Yair Segev
- ID: 215979543
- Email: yairsegev9@gmail.com