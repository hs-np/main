import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class Client extends JFrame {

    private String address;
    private String port;
    private JLabel title= new JLabel("제목");
    private JTextField login= new JTextField();
    private JTextField password= new JTextField();
    private JButton signinButton = new JButton("로그인");
    private JButton signupButton = new JButton("생성");
    private JTextArea serverChat;
    private Socket socket;
    private BufferedWriter bw;
    private BufferedReader br;
    private Thread acceptThread;
    //계속 서버에서의 메시지를 받고 연결을 유지하기 위해 멀티 스레드 결정.

    private JPanel controlPanel;
    private JButton matching = new JButton("매칭");
    private JButton backTurn = new JButton("무르기");
    private JButton turn = new JButton("턴종료");
    private JButton exit = new JButton("나가기");

    private ConnectState connectState = new NotConnected();
    //초기 상태는 매칭전 상태.

    public void setConnectState(ConnectState connectState){
        this.connectState = connectState;
    }
    public ConnectState getConnectState(){
        return this.connectState;
    }

    public Client(String address, String port){
        this.address = address;
        this.port = port;
        this.setBounds(0, 0, 1000, 550);
        this.setTitle("Client GUI");
        startGUI();

        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.setVisible(true);
    }
    public void startGUI(){
        this.add(new JScrollPane(serverChat));

        this.getContentPane().add(paintPanel(), BorderLayout.CENTER);

        serverChat = new JTextArea(20,10);
        this.getContentPane().add(serverChat, BorderLayout.EAST);
        controlPanel = controlPanel();
        this.getContentPane().add(controlPanel, BorderLayout.SOUTH);
        controlPanel.setVisible(false);
    }
    public JPanel paintPanel(){
        JPanel paintPanel = new JPanel();
        paintPanel.setLayout(null);
        title.setBounds(475, 100, 50, 50);
        login.setBounds(400, 300, 140, 25);
        password.setBounds(400, 325, 140, 25);
        signinButton.setBounds(540, 300, 70, 25);
        signupButton.setBounds(540, 325, 70, 25);
        paintPanel.add(title);
        paintPanel.add(login);
        paintPanel.add(password);
        paintPanel.add(signinButton);
        paintPanel.add(signupButton);

        login.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (login.getText().equals("아이디")) {
                    login.setText("");
                    login.setForeground(Color.BLACK);  // 기본 텍스트 색으로 변경
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (login.getText().isEmpty()) {
                    login.setText("아이디");
                    login.setForeground(Color.GRAY);  // placeholder 색으로 변경
                }
            }
        });
        password.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (password.getText().equals("비밀번호")) {
                    password.setText("");
                    password.setForeground(Color.BLACK);  // 기본 텍스트 색으로 변경
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (password.getText().isEmpty()) {
                    password.setText("비밀번호");
                    password.setForeground(Color.GRAY);  // placeholder 색으로 변경
                }
            }
        });

        signinButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try{
                    LoginData loginData = makeloginData("로그인");
                    sendLoginData(loginData);
                    String msg = sendResult();
                    checkSignIn(msg);
                    bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                }
                catch(IOException e1){
                    System.out.println(e1.getMessage());
                }
            }
        });
        signupButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try{
                    LoginData loginData = makeloginData("회원가입");
                    sendLoginData(loginData);
                    String msg = sendResult();
                    checkSignUp(paintPanel,msg);
                    bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                }
                catch(IOException e1){System.out.println(e1.getMessage());}
            }
        });

        return paintPanel;
    }
    public JPanel controlPanel(){
        JPanel controlPanel = new JPanel(new GridLayout(0,4));
        matching.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                requestMatching();
            }
        });
        exit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                endGame();
            }
        });

        backTurn.setEnabled(false);
        turn.setEnabled(false);
        controlPanel.add(matching);
        controlPanel.add(backTurn);
        controlPanel.add(turn);
        controlPanel.add(exit);
        return controlPanel;
    }

    public LoginData makeloginData(String str){
        String idData = login.getText();
        String pwData = password.getText();
        LoginData loginData = new LoginData(idData, pwData, str);
        return loginData;
    }
    public void sendLoginData(LoginData loginData) throws IOException{
        socket = new Socket(address, Integer.parseInt(port));
        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
        br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        oos.writeObject(loginData);
    }
    public String sendResult() throws IOException{
        String msg = br.readLine();
        serverChat.append(msg + "\n");
        return msg;
    }
    public void checkSignIn(String msg) throws IOException{
        switch(msg){
            case "로그인수락":login();
                break;
            default: serverChat.append("로그인 실패: " + msg + "\n");
                break;
        }
    }
    public void checkSignUp(JPanel paintPanel, String msg){
        switch(msg){
            case "회원가입성공":
                JOptionPane.showMessageDialog(paintPanel, "회원가입에 성공했습니다.","성공!", JOptionPane.PLAIN_MESSAGE);
                break;
            case "회원가입실패":
                JOptionPane.showMessageDialog(paintPanel, "회원가입에 실패했습니다.","실패!", JOptionPane.WARNING_MESSAGE);
                break;
        }
    }

    public void startConnect() throws IOException {
        String msg = br.readLine();
        //while ((msg = br.readLine()) != null) {
            if(msg.equals("게임시작")){
                startGame();
            }
            //매칭 방에 사람이 두명 이상 존재하면 바로 팀 매칭이 됨.
            serverChat.append(msg+"\n");
            //displayDotAndBoxGame();
        //}
    }

    interface ConnectState {
        public void endGame(Client client);
        public void requestMatching(Client client);
        public void startGame(Client client);
        public void login(Client client) throws IOException;
    }
    //연결상태에 따라 진행. 게임 진행을 어느 상태인지에 따라 관리함. -> 상태에 따라 동일한 버튼 클릭도 다른 반응이 나오기 때문.
    // ex) 매칭 전 매칭 버튼, 게임 중 매칭 버튼 클릭은 다른 반응을 보여야 함.
    class NotConnected implements ConnectState {

        @Override
        public void endGame(Client client) {

        }
        @Override
        public void requestMatching(Client client) {
            try {
                notConnectedToRequestMatching();
            } catch (IOException e) {
                serverChat.append("매칭 요청 중 오류가 발생했습니다.\n");
            }
        }
        @Override
        public void startGame(Client client) {
            serverChat.append("매칭을 먼저 해야합니다.\n");
        }
        @Override
        public void login(Client client){
            loginSucessConnnect();
        }
    }
    //매칭 전 상태
    class Matching implements ConnectState {

        @Override
        public void endGame(Client client) {

        }

        @Override
        public void requestMatching(Client client) {
            try{
                requestMatchingToNotConnected();
            }
            catch(IOException e){System.out.println(e.getMessage());}
        }

        @Override
        public void startGame(Client client) {requestMatchingToInGame();}

        @Override
        public void login(Client client) {

        }

    }
    //매칭 중인 상태, 게임 진행 전 단계.
    class InGame implements ConnectState {

        @Override
        public void endGame(Client client) {
            try{
                inGameToNotConnected();
            }
            catch(IOException e){
                System.out.println(e.getMessage());
            }
        }

        @Override
        public void requestMatching(Client client) {
            serverChat.append("게임이 이미 진행 중입니다.\n");
        }

        @Override
        public void startGame(Client client) {
            serverChat.append("게임이 이미 진행 중입니다.\n");
        }

        @Override
        public void login(Client client) {

        }


    }
    //게임 중인 상태.

    public void requestMatching(){
        connectState.requestMatching(this);
    }
    public void startGame(){
        connectState.startGame(this);
    }
    public void endGame(){
        connectState.endGame(this);
    }
    public void login() throws IOException{connectState.login(this);}
    //통신을 통한 상태변경 메소드

    public static void main(String[] args){
        String address = "localhost";
        String port = "12345";
        new Client(address, port);
        new Client(address, port);
    }

    public void loginSucessConnnect(){
        title.setVisible(false);
        login.setVisible(false);
        password.setVisible(false);
        signinButton.setVisible(false);
        signupButton.setVisible(false);
        controlPanel.setVisible(true);
        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while(acceptThread == Thread.currentThread()){
                    try{
                        startConnect();
                    }
                    catch(IOException e){

                    }
                }
            }
        });
        serverChat.append("111");
        acceptThread.start();
    }
    public void notConnectedToRequestMatching() throws IOException{
        bw.write("매칭\n");
        bw.flush();
        serverChat.append("클라: 매칭 중...\n");
        setConnectState(new Matching());

        JPanel matchingPanel = new PraticeGamePanel(4); // 4x4 점
        this.getContentPane().removeAll();
        this.getContentPane().add(matchingPanel, BorderLayout.CENTER);
        this.getContentPane().add(serverChat, BorderLayout.EAST);
        this.getContentPane().add(controlPanel, BorderLayout.SOUTH);
        this.revalidate();
        this.repaint();
    }
    public void requestMatchingToNotConnected() throws IOException{
        if (connectState.getClass() == new Matching().getClass()) {
            bw.write("매칭취소\n");
            bw.flush();
            serverChat.append("매칭을 취소합니다.\n");
            setConnectState(new NotConnected());
        }
    }
    public void requestMatchingToInGame() {
        serverChat.append("게임을 시작합니다. 곧 색 선택창이 활성화됩니다.\n");
        matching.setEnabled(false);
        backTurn.setEnabled(true);
        turn.setEnabled(true);
        setConnectState(new InGame());

        JPanel gamePanel = new DotAndBoxGamePanel(4); // 4x4 점
        this.getContentPane().removeAll();
        this.getContentPane().add(gamePanel, BorderLayout.CENTER);
        this.getContentPane().add(serverChat, BorderLayout.EAST);
        this.getContentPane().add(controlPanel, BorderLayout.SOUTH);
        this.revalidate();
        this.repaint();
    }
    public void inGameToNotConnected() throws IOException{
        serverChat.append("게임이 종료되었습니다.");
        setConnectState(new NotConnected());
        matching.setEnabled(true);
        backTurn.setEnabled(false);
        turn.setEnabled(false);
        bw.write("게임종료\n");
        bw.flush();
    }
    //상태에 따른 로직 메소드

    class DotAndBoxGamePanel extends JPanel {
        private int gridSize;
        private boolean[][] horizontalLines;
        private boolean[][] verticalLines;
        private int dotSpacing = 100; // 점 사이 간격
        private Line currentLine; // 현재 선을 저장하는 전역 변수
        private Queue<Point> clickedPoints; // 최근 클릭된 두 점을 저장하는 큐

        public DotAndBoxGamePanel(int gridSize) {
            this.gridSize = gridSize;
            this.horizontalLines = new boolean[gridSize][gridSize - 1];
            this.verticalLines = new boolean[gridSize - 1][gridSize];
            this.currentLine = null; // 초기에는 선이 없도록 설정
            this.clickedPoints = new LinkedList<>(); // 큐 초기화
            setPreferredSize(new Dimension(400, 400));
            setBackground(Color.WHITE);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Point clickedPoint = new Point(e.getX(), e.getY());
                    handleMouseClick(clickedPoint);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            g.setColor(Color.BLACK);

            // 점 그리기
            for (int i = 0; i < gridSize; i++) {
                for (int j = 0; j < gridSize; j++) {
                    int x = 50 + j * dotSpacing;
                    int y = 50 + i * dotSpacing;
                    g.fillOval(x - 5, y - 5, 10, 10);
                }
            }

            // 선 그리기
            g.setColor(Color.BLUE);
            if (currentLine != null) {
                int x1 = 50 + currentLine.p1.x * dotSpacing;
                int y1 = 50 + currentLine.p1.y * dotSpacing;
                int x2 = 50 + currentLine.p2.x * dotSpacing;
                int y2 = 50 + currentLine.p2.y * dotSpacing;
                g.drawLine(x1, y1, x2, y2);
            }
        }

        private void handleMouseClick(Point clickPoint) {
            Point gridPoint = getNearestGridPoint(clickPoint);
            if (gridPoint == null) return;

            clickedPoints.offer(gridPoint); // 클릭된 점을 큐에 추가

            if (clickedPoints.size() == 2) {
                Point firstPoint = clickedPoints.poll(); // 첫 번째 점 제거
                Point secondPoint = clickedPoints.peek(); // 두 번째 점 확인

                // 선의 유효성 검사
                if (isValidLine(firstPoint, secondPoint)) {
                    if (currentLine == null || !currentLine.contains(firstPoint, secondPoint)) {
                        currentLine = new Line(firstPoint, secondPoint); // 선을 새로 설정
                        updateLineArrays(currentLine);
                        repaint();
                    }
                }
            }
        }

        private Point getNearestGridPoint(Point clickPoint) {
            int x = (clickPoint.x - 50 + dotSpacing / 2) / dotSpacing;
            int y = (clickPoint.y - 50 + dotSpacing / 2) / dotSpacing;

            if (x >= 0 && x < gridSize && y >= 0 && y < gridSize) {
                return new Point(x, y);
            }
            return null;
        }

        // 선이 유효한지 검사 (1칸 위, 아래, 좌, 우만 가능)
        private boolean isValidLine(Point firstPoint, Point secondPoint) {
            int dx = Math.abs(firstPoint.x - secondPoint.x);
            int dy = Math.abs(firstPoint.y - secondPoint.y);

            // 1칸 차이 나는 선만 유효 (수평 또는 수직)
            return (dx == 1 && dy == 0) || (dx == 0 && dy == 1);
        }

        private void updateLineArrays(Line line) {
            if (line.p1.x == line.p2.x) { // 수직 선
                int x = line.p1.x;
                int y = Math.min(line.p1.y, line.p2.y);
                verticalLines[y][x] = true;
            } else if (line.p1.y == line.p2.y) { // 수평 선
                int x = Math.min(line.p1.x, line.p2.x);
                int y = line.p1.y;
                horizontalLines[y][x] = true;
            }
        }
    }
}