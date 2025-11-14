import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

// GUI imports
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);

        try {
            switch (mode) {
                case "server":
                    startServer(args);
                    break;
                case "client":
                    startClient(args);
                    break;
                case "client-gui":
                case "gui":
                    startClientGui(args);
                    break;
                default:
                    System.out.println("Unknown mode: " + mode);
                    printUsage();
                    break;
            }
        } catch (IllegalArgumentException ex) {
            System.out.println("Error: " + ex.getMessage());
            printUsage();
        } catch (IOException ex) {
            System.out.println("I/O error: " + ex.getMessage());
        }
    }

    private static void startServer(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("server <port>");
        }

        int port = parsePort(args[1]);
        new ChatServer(port).start();
    }

    private static void startClient(String[] args) throws IOException {
        if (args.length < 4) {
            throw new IllegalArgumentException("client <host> <port> <nickname>");
        }

        String host = args[1];
        int port = parsePort(args[2]);
        String nickname = args[3];
        new ChatClientConsole(host, port, nickname).start();
    }

    private static void startClientGui(String[] args) {
        if (args.length < 4) {
            throw new IllegalArgumentException("client-gui <host> <port> <nickname>");
        }

        String host = args[1];
        int port = parsePort(args[2]);
        String nickname = args[3];

        SwingUtilities.invokeLater(() -> {
            ChatWindow window = new ChatWindow(host, port, nickname);
            window.setVisible(true);
        });
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535.");
            }
            return port;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid port number: " + value);
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java Main server <port>");
        System.out.println("  java Main client <host> <port> <nickname>");
        System.out.println("  java Main client-gui <host> <port> <nickname>");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java Main server 5000");
        System.out.println("  java Main client localhost 5000 coder");
        System.out.println("  java Main client-gui localhost 5000 coder");
    }

    private static final class ChatServer {
        private final int port;
        private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
        private final AtomicInteger guestCounter = new AtomicInteger(1);

        private ChatServer(int port) {
            this.port = port;
        }

        private void start() throws IOException {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("Chat server listening on port " + port);
                while (true) {
                    Socket socket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(socket);
                    clients.add(handler);
                    new Thread(handler, "client-" + socket.getPort()).start();
                }
            }
        }

        private void broadcast(String message) {
            for (ClientHandler client : clients) {
                client.send(message);
            }
            System.out.println(message);
        }

        private final class ClientHandler implements Runnable {
            private final Socket socket;
            private PrintWriter writer;
            private String nickname;

            private ClientHandler(Socket socket) {
                this.socket = socket;
            }

            @Override
            public void run() {
                System.out.println("Connected: " + socket.getRemoteSocketAddress());
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    writer = new PrintWriter(socket.getOutputStream(), true);
                    writer.println("Welcome to the chat! Please enter your nickname:");
                    String proposedName = reader.readLine();
                    if (proposedName == null || proposedName.trim().isEmpty()) {
                        nickname = "Guest" + guestCounter.getAndIncrement();
                    } else {
                        nickname = proposedName.trim();
                    }
                    writer.println("Hello " + nickname + "! Type /quit to exit.");
                    broadcast("** " + nickname + " joined the chat **");
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().equalsIgnoreCase("/quit")) {
                            writer.println("Goodbye!");
                            break;
                        }
                        if (!line.trim().isEmpty()) {
                            broadcast("[" + nickname + "] " + line);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Client error: " + e.getMessage());
                } finally {
                    clients.remove(this);
                    if (nickname != null) {
                        broadcast("** " + nickname + " left the chat **");
                    }
                    closeQuietly();
                }
            }

            private void send(String message) {
                if (writer != null) {
                    writer.println(message);
                }
            }

            private void closeQuietly() {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // Ignore close exceptions
                }
            }
        }
    }

    // Core socket client used by both console and GUI
    private static final class ChatClientCore {
        private final String host;
        private final int port;
        private final String nickname;

        private Socket socket;
        private BufferedReader serverIn;
        private PrintWriter serverOut;
        private Thread readerThread;

        private ChatClientCore(String host, int port, String nickname) {
            this.host = host;
            this.port = port;
            this.nickname = nickname;
        }

        void connect(Consumer<String> onMessage, Consumer<Exception> onError, Runnable onClosed) throws IOException {
            socket = new Socket(host, port);
            serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            serverOut = new PrintWriter(socket.getOutputStream(), true);

            // Send nickname first
            if (nickname != null && !nickname.trim().isEmpty()) {
                serverOut.println(nickname.trim());
            }

            readerThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = serverIn.readLine()) != null) {
                        if (onMessage != null) onMessage.accept(line);
                    }
                } catch (IOException e) {
                    if (onError != null) onError.accept(e);
                } finally {
                    if (onClosed != null) onClosed.run();
                }
            }, "client-reader");
            readerThread.setDaemon(true);
            readerThread.start();
        }

        void send(String msg) {
            if (serverOut != null) {
                serverOut.println(msg);
            }
        }

        void close() {
            try {
                if (serverOut != null) serverOut.println("/quit");
            } catch (Exception ignored) {
            }
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    // Console client (keeps previous behavior)
    private static final class ChatClientConsole {
        private final ChatClientCore core;

        private ChatClientConsole(String host, int port, String nickname) {
            this.core = new ChatClientCore(host, port, nickname);
        }

        private void start() throws IOException {
            System.out.println("Connecting to server...");
            core.connect(
                System.out::println,
                ex -> System.out.println("Connection error: " + ex.getMessage()),
                () -> System.out.println("Connection closed."));

            try (BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))) {
                String userLine;
                while ((userLine = userInput.readLine()) != null) {
                    core.send(userLine);
                    if (userLine.trim().equalsIgnoreCase("/quit")) {
                        break;
                    }
                }
            }
            core.close();
        }
    }

    // Swing GUI client window
    private static final class ChatWindow extends JFrame {
        private final String host;
        private final int port;
        private final String nickname;
        private final ChatClientCore core;

        private final JTextArea messages = new JTextArea();
        private final JTextField input = new JTextField();
        private final JButton sendBtn = new JButton("Send");
        private final JLabel status = new JLabel();

        private ChatWindow(String host, int port, String nickname) {
            super("Chat - " + nickname + " @ " + host + ":" + port);
            this.host = host;
            this.port = port;
            this.nickname = nickname;
            this.core = new ChatClientCore(host, port, nickname);

            buildUi();
            connect();
        }

        private void buildUi() {
            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            setSize(640, 480);
            setLocationByPlatform(true);

            messages.setEditable(false);
            messages.setLineWrap(true);
            messages.setWrapStyleWord(true);

            JScrollPane scroll = new JScrollPane(messages);
            JPanel bottom = new JPanel(new BorderLayout(8, 8));
            bottom.add(input, BorderLayout.CENTER);
            bottom.add(sendBtn, BorderLayout.EAST);

            JPanel top = new JPanel(new BorderLayout());
            top.add(status, BorderLayout.WEST);

            Container cp = getContentPane();
            cp.setLayout(new BorderLayout(8, 8));
            cp.add(top, BorderLayout.NORTH);
            cp.add(scroll, BorderLayout.CENTER);
            cp.add(bottom, BorderLayout.SOUTH);

            // Actions
            Runnable sendAction = () -> {
                String text = input.getText();
                if (text == null) return;
                String msg = text.trim();
                if (msg.isEmpty()) return;
                core.send(msg);
                input.setText("");
                if ("/quit".equalsIgnoreCase(msg)) {
                    dispose();
                }
            };

            sendBtn.addActionListener(e -> sendAction.run());
            input.addActionListener(e -> sendAction.run());

            addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) {
                    core.close();
                }
            });
        }

        private void connect() {
            setStatus("Connecting...");
            try {
                core.connect(
                    this::appendMessage,
                    ex -> appendMessage("[error] " + ex.getMessage()),
                    () -> appendMessage("[system] Connection closed."));
                setStatus("Connected as " + nickname);
            } catch (IOException e) {
                setStatus("Failed to connect: " + e.getMessage());
            }
        }

        private void appendMessage(String line) {
            SwingUtilities.invokeLater(() -> {
                messages.append(line + "\n");
                messages.setCaretPosition(messages.getDocument().getLength());
            });
        }

        private void setStatus(String s) {
            SwingUtilities.invokeLater(() -> status.setText(s));
        }
    }
}
