import java.util.*;

public class QuartoPlayerAgent extends QuartoAgent{

	//Example AI
	public QuartoPlayerAgent(GameClient gameClient, String stateFileName){
		// because super calls one of the super class constructors(you can overload constructors), you need to pass the parameters required.
		super(gameClient, stateFileName);
	}

	public static void main(String[] args){
		//start the server
		GameClient gameClient = new GameClient();

		String ip = null;
		String stateFileName = null;
		//IP must be specified
		if(args.length > 0){
			ip = args[0];
		}else{
			System.out.println("No IP Specified!");
			System.exit(0);
		}
		if(args.length > 1){
			stateFileName = args[1];
		}
		gameClient.connectToServer(ip, 4321);
		QuartoPlayerAgent quartoAgent = new QuartoPlayerAgent(gameClient, stateFileName);
		quartoAgent.play();

		gameClient.closeConnection();
	}
	@Override
	protected String pieceSelectionAlgorithm(){

		MonteCarlo mc = new MonteCarlo(this.timeLimitForResponse - 1000, 1 / Math.sqrt(2), false);
		String bestAction = mc.UCTSearch(this.quartoBoard, null);
		return bestAction;
	}
	@Override
	protected String moveSelectionAlgorithm(int pieceID){

		MonteCarlo mc = new MonteCarlo(this.timeLimitForResponse - 1000, 1 / Math.sqrt(2), false);
		String bestAction = mc.UCTSearch(this.quartoBoard, pieceID);
		return bestAction;
	}
	private boolean chceckIfGameIsWon(){

		System.out.println(this.timeLimitForResponse+"");

		for(int i = 0; i < NUMBER_OF_ROWS; i++){

			if(this.quartoBoard.checkRow(i)){

				System.out.println("Win via row: " + (i) + " (zero - indexed)");
				return true;
			}
		}
		for(int i = 0; i < NUMBER_OF_COLUMNS; i++){

			if(this.quartoBoard.checkColumn(i)){

				System.out.println("Win via column " + (i) + " (zero-indexed)");
				return true;
			}
		}
		if(this.quartoBoard.checkDiagonals()){

			System.out.println("Win via diagonals");
			return true;
		}
		return false;
	}
}

class Node{

	protected ArrayList<Node> children;

	private QuartoBoard board;
	private String action;
	protected ArrayList<String> remainingMoves;

	private int n,q;

	protected Node parent;

	protected boolean player;

	public Node(QuartoBoard board){

		this.children = new ArrayList<Node>();
		this.remainingMoves = new ArrayList<String>();
		this.board = board;
		this.player = true;
	}
	public QuartoBoard getBoard(){
		return this.board;
	}
	public int getN(){
		return n;
	}
	public void setN(int n){
		this.n = n;
	}
	public int getQ(){
		return q;
	}
	public void setQ(int q){
		this.q = q;
	}
	public String getAction(){
		return action;
	}
	public void setAction(String action){
		this.action = action;
	}
	public void addChild(Node child, String action){

		children.add(child);
		child.action = action;
		child.setParentNode(this);
		child.player = child.parent.player;
		this.getRemainingMoves().remove(action);
	}
	public ArrayList<Node> getChildren(){
		return children;
	}
	public void setParentNode(Node newParentNode){
		this.parent = newParentNode;
	}
	public Node getParentNode(){
		return this.parent;
	}
	public ArrayList<String> getRemainingMoves(){
		return this.remainingMoves;
	}
}
class SelectPieceNode extends Node{

	public SelectPieceNode(QuartoBoard board){
		super(board);

		ArrayList<Integer> moves = MonteCarlo.getPossiblePieces(board);

		if(moves.size() == 32){
			this.remainingMoves.add("00000");
			return;
		}
		for(Integer move: moves){
			String action = String.format("%5s", Integer.toBinaryString(move)).replace(' ', '0');
			this.remainingMoves.add(action);
		}
	}
	public void addChild(Node child, String action){
		super.addChild(child, action);
		child.player = !child.parent.player;
	}
}
class SelectMoveNode extends Node{

	public SelectMoveNode(QuartoBoard board){
		super(board);

		ArrayList<int[]> movesList = MonteCarlo.getPossiblePieces(board, piece);

		if(movesList.size() == 25){
			this.remainingMoves.add("2,2");
			return;
		}
		for(int[] moves: movesList){
			String action = moves[0] + "," + moves[1];
			this.remainingMoves.add(action);
		}
	}
	public void setAction(int piece){
		String action = String.format("%5s", Integer.toBinaryString(piece)).replace(' ', '0');
		super.setAction(action);
	}
}
class TerminatingNode extends Node{

	private int value;

	public TerminatingNode(QuartoBoard board, int value){
		super(board);
		this.value = value;
	}
	public void addChild(Node child){
		System.out.println("Error: terminating node cannot have children!");
		System.exit(-1);
	}
	public int getValue(){
		return this.getValue();
	}
}
class MonteCarlo{

	private int timeLimit;
	private double cp;
	private boolean symmetry;

	public MonteCarlo(int timeLimit, double cp, boolean symmetry){
		this.timeLimit = timeLimit;
		this.cp = cp;
		this.symmetry = symmetry;
	}
	public String UCTSearch(QuartoBoard board, Integer piece){

		Node root;

		if(piece == null){
			root = new SelectPieceNode(board);
		}else{
			if(symmetry){
				root = new SelectMoveNode(board, piece);
			}else{
				root = new SelectMoveNode(board);
			}
			((SelectMoveNode) root).setAction(piece);
		}
		long startTime = System.currentTimeMillis();
		long endTime = startTime + (this.timeLimit);

		while(System.currentTimeMillis() < endTime){

			Node child = treePolicy(root);
			int score;

			if(child instanceof SelectMoveNode){

				piece = parsePiece(child.getAction());
				score = defaultPolicy(board, piece, child.player);
			}else if(child instanceof SelectPieceNode){
				int[] move = parseMove(child.getAction());
				QuartoBoard copyBoard = new QuartoBoard(board);
				copyBoard.insertPieceOnBoard(move[0], move[1], piece);
				score = defaultPolicy(copyBoard, null, child.player);
			}else{
				score = ((TerminatingNode) child).getValue();
			}

			backup(child, score);
		}
		printTree("Root", root);
		return bestChild(root, 0).getAction();
	}
	private Node treePolicy(Node node){

		if(node instanceof TerminatingNode)
			return node;
		if(node.getRemainingMoves().size() != 0)
			return expand(node);

		return bestChild(node, this.cp);
	}
	private Node expand(Node node){

		Node child;

		String action = node.getRemainingMoves().get(0);

		if(node instanceof SelectPieceNode){

			if(symmetry){

				int pieceId = parsePiece(action);
				child = new SelectMoveNode(node.getBoard());
			}else{
				child = new SelectMoveNode(node.getBoard());
			}
		}else{

			QuartoBoard copyBoard = new QuartoBoard(node.getBoard());
			int piece = parsePiece(node.getAction());
			int[] move = parseMove(action);
			copyBoard.insertPieceOnBoard(move[0], move[1], piece);

			if(isWin(copyBoard, move[0], move[1])){

				if(node.player)
					child = new TerminatingNode(copyBoard, -1);
				child = new TerminatingNode(copyBoard, 1);
			}else if(copyBoard.checkIfBoardIsFull()){
				child = new TerminatingNode(copyBoard, 0);
			}else{
				child = new SelectPieceNode(copyBoard);
			}
		}
		node.addChild(child, action);

		return child;
	}
	private Node bestChild(Node node, double delta){
		return argmax(node, delta);
	}
	private Node argmax(Node node, double delta){

		ArrayList<Node> children = node.getChildren();

		double maxValue = evaluate(children.get(0), node.getN(), delta);
		Node maxNode = children.get(0);

		for(int i = 1; i < children.size(); i++){

			Node child = children.get(0);

			double value = evaluate(child, node.getN(), delta);

			if(value > maxValue){

				maxValue = value;
				maxNode = child;
			}			
		}
		return maxNode;
	}
	private int parsePiece(String piece){
		return Integer.parseInt(piece, 2);
	}
	private int parseMove(String move){

		String[] moveString = move.split(",");
		int[] move = new int[2];
		move[0] = Integer.parseInt(moveString[0]);
		move[1] = Integer.parseInt(moveString[1]);
		return move;
	}
	protected Boolean isWin(QuartoBoard board, int row, int col){

		if(board.checkRow(row) || board.checkColumn(col) || board.checkDiagonals()){

			return true;
		}
		return false;
	}
	private double evaluate(Node node, int simulations, double delta){
		return (double)node.getQ() / node.getN() + delta * Math.sqrt(2*Math.log(simulations) / node.getN());
	}
	private int defaultPolicy(QuartoBoard board, Integer piece, Boolean player1){

		QuartoBoard copyBoard = new QuartoBoard(board);
		int score;

		if(piece == null){
			piece = randomPieceSelection(copyBoard);
			score = playGame(copyBoard, piece, false);
		}else{
			score = playGame(copyBoard, piece, true);
		}
		return score;;
	}
	private int playGame(QuartoBoard board, int startingPiece, Boolean player1){

		int piece = startingPiece;

		while(true){

			int[] move = randomMove(piece, board);
			board.insertPieceOnBoard(move[0], move[1], piece);

			if(isWin(board, move[0], move[1])){

				if(player1)
					return 1;
				return -1;
			}
			if(board.checkIfBoardIsFull())
				return 0;

			piece = randomPieceSelection(board);

			player1 = !player1;
		}
	}
	protected int randomPieceSelection(QuartoBoard board){
		QuartoBoard copyBoard = new QuartoBoard(board);
		return copyBoard.chooseRandomPieceNotPlayed(100);
	}
	protected int semiRandomPieceSelection(QuartoBoard board){

		boolean skip = false;

		for(int i = 0; i < board.getNumberOfPieces(); i++){

			skip = false;

			if(!board.isPieceOnBoard(i)){

				for(int row = 0; row < board.getNumberOfRows(); row++){

					for(int col = 0; col < board.getNumberOfColumns(); col++){

						if(!board.isSpaceTaken(row, col)){

							QuartoBoard copyBoard = new QuartoBoard(board);
							copyBoard.insertPieceOnBoard(row, col, i);

							if(copyBoard.checkRow(row) || copyBoard.checkColumn(col) || copyBoard.checkDiagonals()){

								skip = true;
								break;
							}
						}
					}
					if(skip){

						break;
					}
				}
				if(!skip){
					return i;
				}
			}
		}
		QuartoBoard copyBoard = new QuartoBoard(board);
		return copyBoard.chooseRandomPieceNotPlayed(100);
	}
	protected int[] randomMove(int pieceID, QuartoBoard board){

		int[] move = new int[2];
		QuartoBoard copyBoard = new QuartoBoard(board);
		move = copyBoard.chooseRandomPosistionNotPlayed(100);

		return move;
	}
	protected int randomPieceSelection(QuartoBoard board){

		QuartoBoard copyBoard = new QuartoBoard(board);
		return copyBoard.chooseRandomPieceNotPlayed(100);
	}
	protected int[] semiRandomMove(int pieceID, QuartoBoard board){

		int[] move = new int[2];

		for(int row = 0; row < board.getNumberOfRows(); row++){

			for(int col = 0; col < board.getNumberOfColumns(); col++){

				if(!board.isSpaceTaken(row, col)){

					QuartoBoard copyBoard = new QuartoBoard(board);
					copyBoard.insertPieceOnBoard(row, col, pieceID);

					if(copyBoard.checkRow(row) || copyBoard.checkColumn(col) || copyBoard.checkDiagonals()){

						move[0] = row;
						move[1] = col;
						return move;
					}
				}
			}
		}
		QuartoBoard copyBoard = new QuartoBoard(board);
		return copyBoard.chooseRandomPosistionNotPlayed(100);
	}
	public static ArrayList<Integer> getPossiblePieces(QuartoBoard board){

		ArrayList<Integer> pieces = new ArrayList<Integer>();

		for(int i = 0; i < board.getNumberOfPieces(); i++){

			if(!board.isPieceOnBoard(i)){

				pieces.add(i);
			}
		}
		return pieces;
	}
	public static boolean areEqualBoards(QuartoBoard b1, QuartoBoard b2){

		for(int i = 0; i < b1.getNumberOfRows(); i++){

			for(int j = 0; j < b1.getNumberOfColumns(); j++){

				if(!areEqualPieces(b1.board[i][j], b2.board[i][j])){

					return false;
				}
			}
		}
		return true;
	}
	public static boolean areEqualPieces(QuartoPiece p1, QuartoPiece p2){

		if(p1 == null && p2 == null){
			return true;
		}else if(p1 == null || p2 == null){
			return false;
		}else{
			return p1.getPieceID() == p2.getPieceID();
		}
	}
	public static QuartoBoard rotateBoard(QuartoBoard board){

		QuartoBoard copyBoard = new QuartoBoard(board);

		for(int i = 0; i < board.getNumberOfRows(); i++){

			for(int j = board.getNumberOfColumns() - 1; j >= 0; j--){

				copyBoard.board[i][board.getNumberOfColumns() - j - 1] = board.board[j][i];
			}
		}
		return copyBoard;
	}
	private static ArrayList<QuartoBoard> getRotatedBoards(QuartoBoard board){

		ArrayList<QuartoBoard> rotateBoards = new ArrayList<QuartoBoard>();
		QuartoBoard b1 = rotateBoard(board);
		QuartoBoard b2 = rotateBoard(b1);
		QuartoBoard b3 = rotateBoard(b2);
		rotateBoards.add(b1);
		rotateBoards.add(b2);
		rotateBoards.add(b3);

		return rotateBoards;

	}
}
