# jb-custom-protocol

### Setup
Open this project in IntelliJ.
Running server.kt will start a server, which binds to a unix domain socket, reads messages and responds accordingly.
The path to the unix domain socket and to the file are the first and second program arguments respectively.
The server needs to be running for communication to work

### Communicating with the server
###### Client
Running client.kt will start a client, which sends messages to the server and prints the server's response to them.
Those messages conform to the standard of the test task. To send invalid messages, tests can be used.
The server can handle multiple clients at once, so they can all communicate with the server at the same time
###### Test
The ServerTest class contains a socketPath variable, which needs to be adjusted to match the socket, the server is listening on.
Running ServerTest will send a variety of messages, both valid and invalid, to the server and test the server's responses.
