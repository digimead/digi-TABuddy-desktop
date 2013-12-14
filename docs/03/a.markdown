#### Console

Console provides command line interface for the application.

There is a singleton, Akka actor, that intermediate between a user and the application.

There is a solid interface with echo method from the application side.

And there are pack of synchronized interfaces like local console, telnet, GUI console and so on from the user side. User input is processed by dynamic set of parser combinators. Result of each correct command is operation invocation.

All interfaces are binded and updated at the same time after successful command execution since it is a single user application by design.

So an everyone could see the same console state at different implementations.

User Input --> Console --> Command --> Future --> Operation --> Result --> Converter --> Output

##### List of the console commands.

<!-- htmlAsMarkdown:Y -->
<table>
  <tr>
    <th>Command</th><th>Description</th><th>Notes</th>
  </tr>
  <tr>
    <td>help</td>
    <td>Show detailed information about command</td>
    <td>Default context: Core. By default list commands that are available from the current context. You may pass *all* argument to get list of all registered commands.</td>
  </tr>
  <tr>
    <td>test</td>
    <td>Test command for development purpose.</td>
    <td>Default context: none.</td>
  </tr>
  <tr>
    <td>info</td>
    <td>Show application information.</td>
    <td>Default context: Core.</td>
  </tr>
  <tr>
    <td>exit</td>
    <td>Exit from application.</td>
    <td>Default context: Core.</td>
  </tr>
</table>
