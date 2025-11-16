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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.border.EmptyBorder;

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

        installModernLookAndFeel();
        SwingUtilities.invokeLater(() -> {
            ChatWindow window = new ChatWindow(host, port, nickname);
            window.setVisible(true);
        });
    }

    private static void installModernLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    return;
                }
            }
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Keep default look & feel if Nimbus is unavailable
        }
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
        private static final int GRID = 8;

        private final String host;
        private final int port;
        private final String nickname;
        private final ChatClientCore core;

        private final JTextArea messages = new JTextArea();
        private final JTextField input = new JTextField();
        private final JButton sendBtn = new AccentButton("Send");
        private final JLabel status = new JLabel();

        private JPanel serverRail;
        private JPanel membersColumn;

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
            setMinimumSize(new Dimension(860, 560));
            setSize(980, 620);
            setLocationByPlatform(true);

            messages.setEditable(false);
            messages.setLineWrap(true);
            messages.setWrapStyleWord(true);
            messages.setFont(messages.getFont().deriveFont(Font.PLAIN, 15f));
            messages.setForeground(Theme.textPrimary);
            messages.setBackground(Theme.surfaceElevated);
            messages.setMargin(new Insets(GRID, GRID, GRID, GRID));

            input.setFont(messages.getFont());
            input.setForeground(Theme.textPrimary);
            input.setCaretColor(Theme.accentSoft);
            input.setOpaque(true);
            input.setBackground(Theme.surface);
            input.setBorder(BorderFactory.createEmptyBorder(GRID, GRID * 2, GRID, GRID * 2));

            sendBtn.setFont(sendBtn.getFont().deriveFont(Font.BOLD, 15f));

            JScrollPane scroll = new JScrollPane(messages);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.getViewport().setBackground(Theme.surfaceElevated);
            scroll.getViewport().setOpaque(true);
            scroll.setOpaque(false);

            GradientPanel background = new GradientPanel();
            background.setLayout(new BorderLayout(0, GRID * 2));
            background.setBorder(new EmptyBorder(GRID * 2, GRID * 2, GRID * 3, GRID * 2));

            JPanel appHeader = buildAppHeader();
            background.add(appHeader, BorderLayout.NORTH);

            JPanel workspace = new JPanel();
            workspace.setOpaque(false);
            workspace.setLayout(new BoxLayout(workspace, BoxLayout.X_AXIS));

            serverRail = buildServerRail();
            JPanel channelColumn = buildChannelColumn();
            JPanel conversation = buildConversationPanel(scroll);
            membersColumn = buildMembersColumn();

            for (JComponent block : new JComponent[] { serverRail, channelColumn, conversation, membersColumn }) {
                block.setAlignmentY(Component.TOP_ALIGNMENT);
            }

            workspace.add(serverRail);
            workspace.add(Box.createRigidArea(new Dimension(GRID * 2, 0)));
            workspace.add(channelColumn);
            workspace.add(Box.createRigidArea(new Dimension(GRID * 2, 0)));
            workspace.add(conversation);
            workspace.add(Box.createRigidArea(new Dimension(GRID * 2, 0)));
            workspace.add(membersColumn);

            background.add(workspace, BorderLayout.CENTER);
            setContentPane(background);

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
            addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) {
                    updateResponsiveLayout();
                }
            });
            updateResponsiveLayout();
        }

        private JPanel buildAppHeader() {
            RoundedPanel bar = new RoundedPanel(18);
            bar.setBackground(Theme.surface);
            bar.setBorder(new EmptyBorder(GRID * 2, GRID * 3, GRID * 2, GRID * 3));
            bar.setLayout(new BorderLayout(GRID * 3, 0));

            JLabel brand = new JLabel("Nebula Chat");
            brand.setFont(brand.getFont().deriveFont(Font.BOLD, 16f));
            brand.setForeground(Theme.textPrimary);

            JLabel context = new JLabel("The Lab / #lobby");
            context.setFont(context.getFont().deriveFont(Font.PLAIN, 13f));
            context.setForeground(Theme.textSecondary);

            JPanel left = new JPanel();
            left.setOpaque(false);
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
            left.add(brand);
            left.add(context);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, GRID, 0));
            actions.setOpaque(false);
            actions.add(new GhostButton("⌘K"));
            actions.add(new GhostButton("Alerts"));
            actions.add(createAvatarChip());

            bar.add(left, BorderLayout.WEST);
            bar.add(actions, BorderLayout.EAST);
            return bar;
        }

        private JPanel buildServerRail() {
            RoundedPanel rail = new RoundedPanel(24);
            rail.setBackground(Theme.surface);
            rail.setBorder(new EmptyBorder(GRID * 2, GRID, GRID * 2, GRID));
            rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
            rail.setPreferredSize(new Dimension(72, Integer.MAX_VALUE));

            rail.add(createServerBubble(nickname, "You", Theme.accent));
            rail.add(Box.createVerticalStrut(GRID * 2));
            rail.add(createServerBubble("H", "Home Hub", new Color(119, 134, 255)));
            rail.add(Box.createVerticalStrut(GRID * 2));
            rail.add(createServerBubble("D", "Dev Space", new Color(145, 101, 255)));
            rail.add(Box.createVerticalStrut(GRID * 2));
            rail.add(createServerBubble("S", "Study", new Color(255, 156, 190)));
            rail.add(Box.createVerticalStrut(GRID * 2));
            rail.add(createServerBubble("M", "Music", new Color(126, 201, 179)));
            rail.add(Box.createVerticalGlue());
            rail.add(createServerBubble("+", "Add Server", Theme.surfaceElevated));
            return rail;
        }

        private JPanel buildChannelColumn() {
            RoundedPanel column = new RoundedPanel(22);
            column.setBackground(Theme.surface);
            column.setBorder(new EmptyBorder(GRID * 2, GRID * 2, GRID * 2, GRID * 2));
            column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
            column.setPreferredSize(new Dimension(220, Integer.MAX_VALUE));

            JLabel title = new JLabel("The Lab");
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
            title.setForeground(Theme.textPrimary);
            column.add(title);

            JLabel subtitle = new JLabel("#discord-inspired");
            subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12f));
            subtitle.setForeground(Theme.textSecondary);
            column.add(Box.createVerticalStrut(4));
            column.add(subtitle);
            column.add(Box.createVerticalStrut(GRID * 2));

            column.add(createChannelCategory("IMPORTANT"));
            column.add(createChannelRow("# announcements", false, true));
            column.add(createChannelRow("# changelog", false, false));
            column.add(Box.createVerticalStrut(GRID * 2));

            column.add(createChannelCategory("GENERAL"));
            column.add(createChannelRow("# lobby", true, false));
            column.add(createChannelRow("# showcase", false, false));
            column.add(createChannelRow("# random", false, true));
            column.add(Box.createVerticalStrut(GRID * 2));

            column.add(createChannelCategory("SOCIAL"));
            column.add(createChannelRow("# memes", false, false));
            column.add(createChannelRow("# music", false, false));
            column.add(Box.createVerticalGlue());
            return column;
        }

        private JPanel buildConversationPanel(JScrollPane scroll) {
            JPanel conversation = new JPanel(new BorderLayout(0, GRID * 2));
            conversation.setOpaque(false);
            conversation.setPreferredSize(new Dimension(460, Integer.MAX_VALUE));

            RoundedPanel header = buildChannelHeader();

            RoundedPanel timeline = new RoundedPanel(24);
            timeline.setBackground(Theme.surfaceElevated);
            timeline.setBorder(new EmptyBorder(GRID * 2, GRID * 2, GRID * 2, GRID * 2));
            timeline.setLayout(new BorderLayout());
            timeline.add(scroll, BorderLayout.CENTER);

            RoundedPanel composer = new RoundedPanel(26);
            composer.setBackground(Theme.surface);
            composer.setBorder(new EmptyBorder(GRID, GRID * 2, GRID, GRID * 2));
            composer.setLayout(new BorderLayout(GRID, 0));

            JPanel composerActions = new JPanel(new FlowLayout(FlowLayout.LEFT, GRID, 0));
            composerActions.setOpaque(false);
            composerActions.add(new GhostButton("+")); 
            composerActions.add(new GhostButton(":)"));

            composer.add(composerActions, BorderLayout.WEST);
            composer.add(input, BorderLayout.CENTER);
            composer.add(sendBtn, BorderLayout.EAST);

            conversation.add(header, BorderLayout.NORTH);
            conversation.add(timeline, BorderLayout.CENTER);
            conversation.add(composer, BorderLayout.SOUTH);
            return conversation;
        }

        private JPanel buildMembersColumn() {
            RoundedPanel column = new RoundedPanel(22);
            column.setBackground(Theme.surface);
            column.setBorder(new EmptyBorder(GRID * 2, GRID * 2, GRID * 2, GRID * 2));
            column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
            column.setPreferredSize(new Dimension(220, Integer.MAX_VALUE));

            column.add(createSectionLabel("ONLINE — 3"));
            column.add(createMemberRow("You", "Owner", Theme.success));
            column.add(createMemberRow("Aurora", "Moderator", Theme.accentSoft));
            column.add(createMemberRow("Carter", "Member", new Color(255, 200, 120)));
            column.add(Box.createVerticalStrut(GRID * 2));

            column.add(createSectionLabel("OFFLINE"));
            column.add(createMemberRow("Drew", "Member", Theme.surfaceElevated));
            column.add(createMemberRow("Mina", "Member", Theme.surfaceElevated));
            column.add(Box.createVerticalGlue());
            return column;
        }

        private JLabel createChannelCategory(String text) {
            JLabel label = new JLabel(text);
            label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
            label.setForeground(new Color(119, 129, 156));
            label.setBorder(new EmptyBorder(GRID, 0, GRID / 2, 0));
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            return label;
        }

        private JComponent createChannelRow(String name, boolean active, boolean unread) {
            RoundedPanel row = new RoundedPanel(18);
            row.setBackground(active ? Theme.surfaceElevated : new Color(0, 0, 0, 0));
            row.setBorder(new EmptyBorder(GRID, GRID * 2, GRID, GRID * 2));
            row.setLayout(new BorderLayout(GRID, 0));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel indicator = new JPanel();
            indicator.setPreferredSize(new Dimension(4, 4));
            indicator.setMaximumSize(new Dimension(4, Integer.MAX_VALUE));
            indicator.setOpaque(true);
            indicator.setBackground(active ? Theme.accent : unread ? Theme.accentSoft : new Color(0, 0, 0, 0));
            row.add(indicator, BorderLayout.WEST);

            JLabel label = new JLabel(name);
            label.setFont(label.getFont().deriveFont(active ? Font.BOLD : Font.PLAIN, 14f));
            label.setForeground(active ? Theme.textPrimary : Theme.textSecondary);
            row.add(label, BorderLayout.CENTER);

            if (active) {
                row.add(createChip("LIVE", Theme.success, Theme.background), BorderLayout.EAST);
            } else if (unread) {
                row.add(createChip("NEW", Theme.accentSoft, Theme.background), BorderLayout.EAST);
            }
            return row;
        }

        private JLabel createSectionLabel(String name) {
            JLabel label = new JLabel(name);
            label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
            label.setForeground(new Color(129, 137, 164));
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            label.setBorder(new EmptyBorder(0, 0, GRID, 0));
            return label;
        }

        private JComponent createMemberRow(String name, String role, Color accent) {
            JPanel row = new JPanel(new BorderLayout(GRID, 0));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(GRID, 0, GRID, 0));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            RoundedPanel avatar = new RoundedPanel(999);
            avatar.setBackground(accent);
            avatar.setPreferredSize(new Dimension(36, 36));
            avatar.setLayout(new BorderLayout());

            String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            JLabel avatarLabel = new JLabel(initial, SwingConstants.CENTER);
            avatarLabel.setForeground(Theme.background);
            avatarLabel.setFont(avatarLabel.getFont().deriveFont(Font.BOLD, 13f));
            avatar.add(avatarLabel, BorderLayout.CENTER);

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

            JLabel nameLabel = new JLabel(name);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
            nameLabel.setForeground(Theme.textPrimary);

            JLabel roleLabel = new JLabel(role);
            roleLabel.setFont(roleLabel.getFont().deriveFont(Font.PLAIN, 12f));
            roleLabel.setForeground(Theme.textSecondary);

            text.add(nameLabel);
            text.add(roleLabel);

            row.add(avatar, BorderLayout.WEST);
            row.add(text, BorderLayout.CENTER);
            return row;
        }

        private RoundedPanel createChip(String text, Color bg, Color fg) {
            RoundedPanel chip = new RoundedPanel(999);
            chip.setBackground(bg);
            chip.setBorder(new EmptyBorder(2, 8, 2, 8));
            JLabel label = new JLabel(text.toUpperCase(Locale.ROOT));
            label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
            label.setForeground(fg);
            chip.add(label);
            return chip;
        }

        private JComponent createAvatarChip() {
            RoundedPanel chip = new RoundedPanel(999);
            chip.setBackground(Theme.surfaceElevated);
            chip.setBorder(new EmptyBorder(4, 10, 4, 14));
            chip.setLayout(new BorderLayout(GRID, 0));

            RoundedPanel avatar = new RoundedPanel(999);
            avatar.setBackground(Theme.accent);
            avatar.setPreferredSize(new Dimension(28, 28));
            JLabel initial = new JLabel(nickname.substring(0, 1).toUpperCase(Locale.ROOT), SwingConstants.CENTER);
            initial.setForeground(Theme.background);
            initial.setFont(initial.getFont().deriveFont(Font.BOLD, 12f));
            avatar.setLayout(new BorderLayout());
            avatar.add(initial, BorderLayout.CENTER);

            JLabel nameLabel = new JLabel(nickname);
            nameLabel.setForeground(Theme.textPrimary);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 13f));

            chip.add(avatar, BorderLayout.WEST);
            chip.add(nameLabel, BorderLayout.CENTER);
            return chip;
        }

        private JComponent createServerBubble(String label, String tooltip, Color color) {
            RoundedPanel bubble = new RoundedPanel(999);
            bubble.setBackground(color);
            bubble.setPreferredSize(new Dimension(48, 48));
            bubble.setMaximumSize(new Dimension(48, 48));
            bubble.setAlignmentX(Component.CENTER_ALIGNMENT);
            bubble.setLayout(new BorderLayout());
            bubble.setToolTipText(tooltip);

            JLabel text = new JLabel(label, SwingConstants.CENTER);
            text.setForeground(Color.WHITE);
            text.setFont(text.getFont().deriveFont(Font.BOLD, 14f));
            bubble.add(text, BorderLayout.CENTER);

            return bubble;
        }

        private RoundedPanel buildChannelHeader() {
            RoundedPanel header = new RoundedPanel(22);
            header.setBackground(Theme.surface);
            header.setBorder(new EmptyBorder(GRID * 2, GRID * 3, GRID * 2, GRID * 3));
            header.setLayout(new BorderLayout(GRID * 3, 0));

            JLabel channelLabel = new JLabel("# lobby");
            channelLabel.setFont(channelLabel.getFont().deriveFont(Font.BOLD, 22f));
            channelLabel.setForeground(Theme.textPrimary);

            JLabel subtitle = new JLabel("Connected to " + host + ":" + port + " as " + nickname);
            subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 13f));
            subtitle.setForeground(Theme.textSecondary);

            status.setFont(status.getFont().deriveFont(Font.PLAIN, 12f));
            status.setForeground(Theme.textSecondary);

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(channelLabel);
            text.add(Box.createVerticalStrut(4));
            text.add(subtitle);
            text.add(Box.createVerticalStrut(2));
            text.add(status);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, GRID, 0));
            actions.setOpaque(false);
            actions.add(new GhostButton("Search"));
            actions.add(new GhostButton("Pins"));
            actions.add(new GhostButton("Settings"));

            header.add(text, BorderLayout.CENTER);
            header.add(actions, BorderLayout.EAST);
            return header;
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
            SwingUtilities.invokeLater(() -> {
                status.setText(s);
                String lower = s.toLowerCase(Locale.ROOT);
                if (lower.contains("fail") || lower.contains("error")) {
                    status.setForeground(Theme.danger);
                } else if (lower.contains("connect")) {
                    status.setForeground(Theme.success);
                } else {
                    status.setForeground(Theme.textSecondary);
                }
            });
        }

        private void updateResponsiveLayout() {
            int width = getWidth();
            if (membersColumn != null) {
                membersColumn.setVisible(width >= 1040);
            }
            if (serverRail != null) {
                serverRail.setVisible(width >= 820);
            }
        }
    }

    private static final class Theme {
        private static final Color background = new Color(5, 8, 16);
        private static final Color surface = new Color(15, 18, 30);
        private static final Color surfaceElevated = new Color(24, 27, 43);
        private static final Color textPrimary = new Color(233, 235, 245);
        private static final Color textSecondary = new Color(160, 169, 194);
        private static final Color accent = new Color(99, 111, 255);
        private static final Color accentSoft = new Color(140, 149, 255);
        private static final Color success = new Color(128, 206, 168);
        private static final Color danger = new Color(255, 158, 172);
    }

    private static final class GradientPanel extends JPanel {
        private GradientPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, Theme.background, 0, getHeight(), new Color(7, 12, 24)));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class RoundedPanel extends JPanel {
        private final int arc;

        private RoundedPanel(int arc) {
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class AccentButton extends JButton {
        private AccentButton(String text) {
            super(text);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setBorder(new EmptyBorder(10, 24, 10, 24));
            setForeground(Color.WHITE);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = Theme.accent;
            if (getModel().isPressed()) {
                base = base.darker();
            } else if (getModel().isRollover()) {
                base = base.brighter();
            }
            g2.setColor(base);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 28, 28);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class GhostButton extends JButton {
        private GhostButton(String text) {
            super(text);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setForeground(Theme.textSecondary);
            setBorder(new EmptyBorder(6, 12, 6, 12));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int alpha = getModel().isRollover() ? 150 : 90;
            Color base = new Color(83, 92, 126, alpha);
            g2.setColor(base);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
