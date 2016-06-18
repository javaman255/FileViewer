//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
//
//								F i l e V i e w e r
//
//	This is a stand-alone utility which displays the contents of a file in hex and in
//	UTF-8.
//
//	Bugs:
//	The Position panel is taller than the Hex or UTF-8 panels.
//
//
//	Enhancements:
//	Add support for UTF-16, etc.
//	Add ability to resize window and the components will all resize appropriately.
//	Grey out the unused right hand portion of the UTF-8 panel.
//	Allow user to specify the encoding.
//	Allow user to select a font.
//	Support a .ini file to store user's choices.
//	Remember the last directory that the user opened and use that for starting point.
//	Add ability to select text from the screen.
//	Add hex offset in to the file (the "position" in hex.)
//	ALlow to enter hex address in the Go To field.
//	Catch UTF-8 errors and display a period ('.').
//	Add a search function.
//	Add ability to edit the file in hex, ASCII or UTF-8.  Inserts near the beginning of
//		very large files will be slow, don't do it one character at a time.  Have a
//		separate field where you type the text to be entered.  Program can then move every
//		thing up length() bytes at once, rather than once for each byte.
//
//	Fixed:
//	All three columns are now same height.
//
//	Other Windows:
//	1 byte integer (signed and unsigned)
//	2 byte integer (signed and unsigned)
//	4 byte integer (signed and unsigned)
//	8 byte integer (signed and unsigned)
//	4 byte float
//	8 byte float
//	Bit values
//	Unicode Value if UTF-8, etc.
//
//
//	I don't see any reason (other than some dumb language restraint) that any
//		of these should have to begin on any particular byte boundary.  That means a lot 
//		of screen space to display all the possibilities.  Maybe make these various
//		optional windows.  If you know that you are looking for 4 byte addresses, then
//		open the window to display them, otherwise, keep it closed.
//	Check IEEE to see other data formats that are defined especially timestamp or date.
//		If they have defined date, that could be very useful.  When creating a string Java
//		supports "US-ASCII", "ISO-8859-1" (Latin 1), "UTF-8", "UTR1116BE", "UTF-16LE" and
//		"UTF-16".
//	Maybe user selects a location in the hex or ascii portion and selects all the ways he
//		wants the data beginning at that address to be displayed.
//
//	Comment:
//		Using graphics to print the UTF-8.  I couldn't find a font that was, free,
//			universal, monospace and contained the majority of the unicode characters.  I
//			used Graphics in order to emulate a monospaced font.  That gave me more
//			freedom in choosing the font.
//
//		See http://www.programcreek.com/java-api-examples/java.nio.charset.CharsetDecoder
//			for examples for decoding utf-8
//
// copyright 2013-2016 by William Rice
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX--XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

import java.awt.BorderLayout;
import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.JTextPane;
import javax.swing.JTextArea;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;

//import com.william_rice.FileInventory.DrawPanel;

import javax.swing.ImageIcon;


//import FileViewer3.GraphicsArea;
//import FileViewer3.Utf8Char;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.awt.Dimension;

//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
//										FileViewer
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX^^XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
public class FileViewer extends JFrame {

	private JPanel contentPane;
	private JTextField jFileName;
	private JTextArea taPosition = new JTextArea();
	private JTextArea taHex = new JTextArea();
	private JTextField tfAddress;
	
	JButton btnGoTo = new JButton("Go To");
	JButton btnStart = new JButton("|<");
	JButton btnPageUp = new JButton("<<");
	JButton btnLineUp = new JButton("<");
	JButton btnLineDown = new JButton(">");
	JButton btnPageDown = new JButton(">>");
	JButton btnEnd = new JButton(">|");

	private RandomAccessFile raf = null;
	private int requestCols = 16;
//	private int requestCols = 6;
	private int requestRows = 16;
//	private int requestRows = 3;
	private long requestPos;
	
	long readPos;
	long readLen;
	private long rafLen;
	private int buffLen = 3;
	int fntSzUtf8 = 18;
	int fntSzHex = 16;
	private int requestPage = requestCols * requestRows;
	Font fntUtf8 = new Font("Lucida", Font.PLAIN, fntSzUtf8);
	String[] strUtf8 = new String[requestRows];
	GraphicsArea cmpUtf8 = new GraphicsArea();

//****************************************************************************************
//											main
//******************************************^^********************************************
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					FileViewer frame = new FileViewer();
					frame.setVisible(true);
					frame.setResizable(false);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

//****************************************************************************************
//	FileViewer()
//******************************************^^********************************************
	public FileViewer() {
		try {
			UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException e2) {
			e2.printStackTrace();
		}

		setTitle("File Viewer Utility");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 1033, 460);
		setResizable(false);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(0, 0));
		setContentPane(contentPane);
		
//========================================================================================
		JPanel pnlFile = new JPanel();
		contentPane.add(pnlFile, BorderLayout.NORTH);
		
//----------------------------------------------------------------------------------------
//		btnFileDlg
//------------------------------------------^^--------------------------------------------
		JButton btnFileDlg = new JButton("Browse...");
		btnFileDlg.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				final JFileChooser fileDialog = new JFileChooser();
				fileDialog.setApproveButtonText("Select");
				fileDialog.showOpenDialog(contentPane);
				File openFile = fileDialog.getSelectedFile();
				String path = openFile.getPath();
				jFileName.setText(path);
			}
		});
		pnlFile.add(btnFileDlg);
		
		JLabel lblNewLabel = new JLabel("File Name");
		pnlFile.add(lblNewLabel);
		
		jFileName = new JTextField();
		pnlFile.add(jFileName);
		jFileName.setColumns(30);
		
//----------------------------------------------------------------------------------------
//		btnOpen
//------------------------------------------^^--------------------------------------------
		JButton btnOpen = new JButton("Open");
		btnOpen.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String fromFile = jFileName.getText();
				if (fromFile.isEmpty()) {
					return;
				}
				try {
					raf = new RandomAccessFile(fromFile, "r");
					rafLen = raf.length();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				btnGoTo.setEnabled(true);
				btnStart.setEnabled(true);
				btnPageUp.setEnabled(true);
				btnLineUp.setEnabled(true);
				btnLineDown.setEnabled(true);
				btnPageDown.setEnabled(true);
				btnEnd.setEnabled(true);
				requestPos = 0;
				display();
			}
		});
		pnlFile.add(btnOpen);
		
//========================================================================================
		JPanel pnlNav = new JPanel();
		contentPane.add(pnlNav, BorderLayout.SOUTH);
		
//----------------------------------------------------------------------------------------
//		tfAddress
//------------------------------------------^^--------------------------------------------
		tfAddress = new JTextField();
		tfAddress.setFont(new Font("Courier New", Font.PLAIN, 12));
		pnlNav.add(tfAddress);
		tfAddress.setColumns(12);
		
//----------------------------------------------------------------------------------------
//		btnGoTo
//------------------------------------------^^--------------------------------------------
		btnGoTo.setEnabled(false);
		btnGoTo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				long lngAddr;
				
				String strSign;
				String strIncr;
				long lngIncr;
				
				String strAddr = "";
				strAddr = tfAddress.getText();
				strAddr = strAddr.trim();
				strAddr = strAddr.replace(",", "");

				if (strAddr.length() > 0) {
					strSign = strAddr.substring(0, 1);
					if ((strSign.equals("+")) || (strSign.equals("-"))) {
						strIncr = strAddr.substring(1, strAddr.length());
						if (isInteger(strIncr)) {
							lngIncr = Long.parseLong(strIncr);
							if (strSign.equals("+")) {
								lngAddr = requestPos + lngIncr;
							} else {
								lngAddr = requestPos - lngIncr;
							}
							if (lngAddr > (rafLen - requestPage)) {
								lngAddr = rafLen - requestPage;
							}
							if (lngAddr < 0) {
								lngAddr = 0;
							}
							requestPos = lngAddr;
							display();
							
						} else {
						}
					} else
					if (isInteger(strAddr)) {
						lngAddr = Long.parseLong(strAddr);
						if (lngAddr > (rafLen - requestPage)) {
							lngAddr = rafLen - requestPage;
						}
						if (lngAddr < 0) {
							lngAddr = 0;
						}
						requestPos = lngAddr;
						display();
					}
				}
				
			}
		});
		pnlNav.add(btnGoTo);
		
//----------------------------------------------------------------------------------------
//		btnStart
//------------------------------------------^^--------------------------------------------
		btnStart.setEnabled(false);
		btnStart.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				requestPos = 0;
				display();
			}
		});
		pnlNav.add(btnStart);
		
//----------------------------------------------------------------------------------------
//		btnPageUp
//------------------------------------------^^--------------------------------------------
		btnPageUp.setEnabled(false);
		btnPageUp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				requestPos -= requestPage;
				if (requestPos < 0) {
					requestPos = 0;
				}
				display();
			}
		});
		pnlNav.add(btnPageUp);
		
//----------------------------------------------------------------------------------------
//		btnLineUp
//------------------------------------------^^--------------------------------------------
		btnLineUp.setEnabled(false);
		btnLineUp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				requestPos -= requestCols;
				if (requestPos < 0) {
					requestPos = 0;
				}
				display();
			}
		});
		pnlNav.add(btnLineUp);
		
//----------------------------------------------------------------------------------------
//												btnLineDown
//------------------------------------------^^--------------------------------------------
		btnLineDown.setEnabled(false);
		btnLineDown.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				requestPos += requestCols;
				if (requestPos > (rafLen - requestPage)) {
					requestPos = rafLen - requestPage;
				}
				if (requestPos < 0) {
					requestPos = 0;
				}
				display();
			}
		});
		pnlNav.add(btnLineDown);
		
//----------------------------------------------------------------------------------------
//		btnPageDown
//------------------------------------------^^--------------------------------------------
		btnPageDown.setEnabled(false);
		btnPageDown.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				requestPos += requestPage;
				if (requestPos > (rafLen - requestPage)) {
					requestPos = rafLen - requestPage;
				}
				if (requestPos < 0) {
					requestPos = 0;
				}
				display();
			}
		});
		pnlNav.add(btnPageDown);
		
//----------------------------------------------------------------------------------------
//		btnEnd
//------------------------------------------^^--------------------------------------------
		btnEnd.setEnabled(false);
		btnEnd.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				requestPos = rafLen - requestPage;
				if (requestPos < 0) {
					requestPos = 0;
				}
				display();
			}
		});
		pnlNav.add(btnEnd);
		
//----------------------------------------------------------------------------------------
//										position
//------------------------------------------^^--------------------------------------------
		JPanel pnlDummy = new JPanel();
		contentPane.add(pnlDummy, BorderLayout.WEST);
		JPanel pnlPostion = new JPanel();
		pnlPostion.setPreferredSize(new Dimension(120, 330));
		pnlPostion.setBorder(new LineBorder(new Color(0, 0, 0)));
		pnlDummy.add(pnlPostion, BorderLayout.CENTER);
		pnlPostion.setLayout(new BorderLayout(0, 0));
		
		JLabel lblPosition = new JLabel("Position");
		lblPosition.setVerticalAlignment(SwingConstants.TOP);
		pnlPostion.add(lblPosition, BorderLayout.NORTH);
		
		taPosition.setFont(new Font("Courier New", Font.PLAIN, fntSzHex));
		taPosition.setRows(requestRows);
		taPosition.setColumns(12);
		pnlPostion.add(taPosition, BorderLayout.CENTER);
		
//========================================================================================
		JPanel pnlData = new JPanel();
		contentPane.add(pnlData, BorderLayout.CENTER);
		pnlData.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
		
//----------------------------------------------------------------------------------------
//											hex
//------------------------------------------^^--------------------------------------------
		JPanel pnlHex = new JPanel();
		pnlHex.setPreferredSize(new Dimension(540, 330));
		pnlHex.setBorder(new LineBorder(new Color(0, 0, 0)));
		pnlData.add(pnlHex);
		pnlHex.setLayout(new BorderLayout(0, 0));
		
		JLabel lblHex = new JLabel("Hex");
		pnlHex.add(lblHex, BorderLayout.NORTH);
		taHex.setFont(new Font("Courier New", Font.PLAIN, fntSzHex));
		taHex.setRows(requestRows);
		taHex.setColumns((int) (requestCols * 3.25));
		
		pnlHex.add(taHex, BorderLayout.CENTER);
		
//----------------------------------------------------------------------------------------
//										pnlUtf8
//------------------------------------------^^--------------------------------------------
		JPanel pnlUtf8 = new JPanel();
		pnlUtf8.setPreferredSize(new Dimension(320, 330));
		pnlUtf8.setMinimumSize(new Dimension(40, 40));
		pnlUtf8.setLayout(new BorderLayout(0, 0));
		pnlUtf8.setBorder(new LineBorder(new Color(0, 0, 0)));
		
		JLabel lblUtf8 = new JLabel("UTF-8");
		pnlUtf8.add(lblUtf8, BorderLayout.NORTH);
	    cmpUtf8.setFont(fntUtf8);
		JPanel panel = new JPanel();
		panel.setPreferredSize(new Dimension(320, 320));
		panel.setMinimumSize(new Dimension(40, 40));
		cmpUtf8.setPreferredSize(new Dimension(140, 50));
		cmpUtf8.setPreferredSize(new Dimension(320, 320));
		pnlUtf8.add(panel, BorderLayout.CENTER);
		
	    panel.setBackground(Color.WHITE);;
	    panel.add(cmpUtf8);
		pnlData.add(pnlUtf8);
		
	}


//****************************************************************************************
//											display()
//******************************************^^********************************************
	public void display() {
		int lineCnt = 0;
		DecimalFormat formatter = new DecimalFormat("#,##0");
		long dispPos;
		long inPos;
		int preBuffLen;
		String strPos = "";
		String strHex = "";
		
		readPos = Math.max(0, requestPos - buffLen);
		preBuffLen = (int) (requestPos - readPos);
		readLen = preBuffLen + requestPage + buffLen;
		if (readLen + readPos > rafLen) {
			readLen = rafLen - readPos;
		}
		byte[] rafArray = new byte[(int) readLen];
		byte[] rowBytArr = new byte[requestCols];

//----------------------------------------------------------------------------------------
//									Initialize accumUtf8 array
//------------------------------------------^^--------------------------------------------
		for(int i = 0; i < requestRows; i++) {
			strUtf8[i] = "";
		}

//----------------------------------------------------------------------------------------
//											Read File
//------------------------------------------^^--------------------------------------------
		rafArray = fileReader(raf, rafArray);
		dispPos = requestPos;
		inPos = 0;
		
//----------------------------------------------------------------------------------------
//									Format strings for display
//------------------------------------------^^--------------------------------------------
		for (lineCnt = 0; lineCnt < requestRows; lineCnt++) {
			for (int i = (int) (readPos - readPos); i < requestCols; i++) {
				rowBytArr[i] = rafArray[(int)inPos + i];
			}
			strPos += "" + formatter.format(dispPos) + "\n";
			strHex += bytesToHex(rowBytArr) + "\n";
			dispPos += requestCols;
			inPos += requestCols;
		}
		strPos = strPos.substring(0,strPos.length() - 1);
		strHex = strHex.substring(0,strHex.length() - 1);
		
//----------------------------------------------------------------------------------------
//								Format strings for UTF-8  Display
//------------------------------------------^^--------------------------------------------
		int dspIdx = 0;
		String holdStr = "";
		String s = "";
		lineCnt = 0;
		int readIdx;
  		for (readIdx = 0; readIdx < rafArray.length; ) {
			Utf8Char uc = new Utf8Char(rafArray, readIdx);
			readIdx += uc.consumes;
			if (readIdx < (preBuffLen + 1 )) { continue; }
			s = uc.character;
String a;
a = showUsYourBits(s);
if ((uc.consumes > 1) && (a.equalsIgnoreCase("0011 1111"))) {
	s = ".";
}
			dspIdx = readIdx - preBuffLen;
			holdStr += s;
			if (dspIdx < ((lineCnt + 1) * requestCols)) { continue; }
			if (lineCnt < requestRows) {
				strUtf8[lineCnt] = holdStr;
				holdStr = "";
			}
			lineCnt++;
		}
		
//----------------------------------------------------------------------------------------
//								move strings to the output screen
//------------------------------------------^^--------------------------------------------
		taPosition.setText(strPos);
		taPosition.paintImmediately(0, 0, taPosition.getWidth(), taPosition.getHeight());
		taHex.setText(strHex);
		taHex.paintImmediately(0, 0, taHex.getWidth(), taHex.getHeight());
		cmpUtf8.repaint();

	}

//****************************************************************************************
//											fileReader()
//******************************************^^********************************************
	public  byte[] fileReader(RandomAccessFile raf, byte[] inArray) {
		int len1 = 0;
		int len2 = 0;
		len1 = Math.min(inArray.length, (int) ((rafLen - readPos)));
		byte[] outArray = new byte[len1];
		
		try {
			raf.seek(readPos);
			len2 = raf.read(outArray);
			if (len1 != len2) {
				System.out.println("len1 = " + len1);
				System.out.println("len2 = " + len2);
				int a = 0;
				int b = 0;
				a /= b;
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return outArray;
	}

//****************************************************************************************
//											bytesToHex()
//******************************************^^********************************************
	public static String bytesToHex(byte[] bytes) {
		final char[] hexArray = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
		char[] hexChars = new char[bytes.length * 2];
		int v;
		int j;
		for (j = 0; j < bytes.length; j++) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		String str1 = new String(hexChars);
		String str2 = "";
		for (j = 0; j < str1.length(); j++) {
			str2 += str1.substring(j, j + 1);
			if (j % 2 == 1) {
				str2 += " ";
				if (j % 8 == 7) {
					str2 += ". ";
				}
			}
		}
		str2 = str2.substring(0, str2.length() - 3);
		return str2;
	}
	
//****************************************************************************************
//											isInteger()
//******************************************^^********************************************
	public static boolean isInteger(String str) {
		try {
			int d = Integer.parseInt(str);
		} catch (NumberFormatException nfe) {
			return false;
		}
		return true;
	}
	
//****************************************************************************************
//												arrayPiece()
//******************************************^^********************************************
	public static byte[] arrayPiece(byte[] inBytes, int copyStart, int copyLen) {
		byte[] returnArray = new byte[1];
		returnArray = new byte[copyLen];
		int i;
		int len;
		
		len = Math.min(copyLen, inBytes.length - copyStart);
		for (i = 0; i < len; i++) {
			returnArray[i] = inBytes[i + copyStart];
		}
		return returnArray;
	}

//****************************************************************************************
//											dspBits()
//		Constructs a String of 1's and 0's representing the bit pattern of the object.
//		Accepts byte, char, int and long.
//		Restrictions on which types can be used with bitwise operators prevents other object
//			types from being available.  In particular float, double and Object.
//******************************************^^********************************************
	public static String showUsYourBits(Object inObj) {
		String str = "";
		String outStr = "";
		byte inByte = 0;
		char inChar = 0;
		int inInt = 0;
		long inLong = 0;
		String tempStr = "";
		int inSigLen;
		String inString;
		
		if (inObj instanceof Byte) {
			inByte = (byte) inObj;
			for (int i = 0; i < 8; i++) {
				if ((inByte & 0b10000000) == 0b10000000) { str += "1"; } else { str += "0"; }
				inByte = (byte) (inByte << 1);
			}
			tempStr = "00000000";
		} else if (inObj instanceof Character) {
			inChar = (char) inObj;
			for (int i = 0; i < 16; i++) {
				if ((inChar & 0b1000000000000000) == 0b1000000000000000) { str += "1"; } else { str += "0"; }
				inChar = (char) (inChar << 1);
			}
			tempStr = "0000000000" + "000000";
		} else if (inObj instanceof Integer) {
			inInt = (int) inObj;
			str = Integer.toBinaryString(inInt);
			tempStr = "0000000000" + "0000000000" + "0000000000" +"00";
		} else if (inObj instanceof Long) {
			inLong = (long) inObj;
			str = Long.toBinaryString(inLong);
			tempStr = "0000000000" + "0000000000" + "0000000000" + "0000000000" + "0000000000" + "0000000000" + "0000";
		} else if (inObj instanceof String) {
			inString = inObj.toString();
			char[] ca;
			ca = inString.toCharArray();
			tempStr = "";
			for (int charCnt = 0; charCnt < ca.length; charCnt++) {
				inChar = ca[charCnt];
				for (int i = 0; i < 16; i++) {
					if ((inChar & 0b1000000000000000) == 0b1000000000000000) { str += "1"; } else { str += "0"; }
					inChar = (char) (inChar << 1);
				}
				tempStr += "0000000000" + "000000";
			}
			
		}

		inSigLen = tempStr.length() - str.length();
		tempStr = tempStr.substring(0, inSigLen) + str;
		outStr = "";
		for (int i = 0; i < tempStr.length(); i+=4) {
			outStr += tempStr.substring(i, i + 4);
			outStr += " ";
			if ((i + 4) % 8 == 0) { 
				outStr += "~ ";
			}
		}
		outStr = outStr.substring(0, outStr.length());
		return outStr;
	}
	
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
//		DrawPanel
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX^^XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
	public class GraphicsArea extends JPanel
		{
		public GraphicsArea()							// set up graphics window
			{
			super();
			}
		
		public void paintComponent(Graphics g)		// draw graphics in the panel
			{
			int left, top, width, height;
			left = 0;
			top = -4;
			width = getWidth();						// width of window in pixels
			height = getHeight();					// height of window in pixels
			height = (fntSzUtf8 + 2) * requestRows;
			width = (fntSzUtf8 + 2) * requestCols;
			g.fillRect(left, top, width, height);
			super.paintComponent(g);				// call superclass to make panel display correctly
			setBackground(Color.WHITE);
			
			String tempStr;
			for (int i = 0; i < requestRows; i++) {
				tempStr = strUtf8[i];
				if (tempStr == null) { continue; }
				for (int j = 0; j < tempStr.length(); j++) {
					g.drawString(tempStr.substring(j, j+1), (j+1)*(fntSzUtf8+1), (i+1)*(fntSzUtf8+1)-4);
				}
			}
			
		}

	}
	
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
//										U t f 8 C h a r
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX^^XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
	class Utf8Char{
		String character;
		int consumes;
		
//****************************************************************************************
//											Utf8Char
//******************************************^^********************************************
		public Utf8Char(byte[] bytes, int startPos) {
			int expectedLen;
			String holdStr;
			
			String display;
			display = toBitString(bytes[startPos]);
			
//											Get Length
//------------------------------------------^^--------------------------------------------
			if		((bytes[startPos] & 0b10000000) == 0b00000000) expectedLen = 1;
			else if ((bytes[startPos] & 0b11100000) == 0b11000000) expectedLen = 2;
			else if ((bytes[startPos] & 0b11110000) == 0b11100000) expectedLen = 3;
			else if ((bytes[startPos] & 0b11111000) == 0b11110000) expectedLen = 4;
			else {
				character = ".";
				consumes = 1;
				return;
			}
			
			if (startPos + expectedLen > bytes.length) {
				character = ".";
				consumes = 1;
				return;
			}
			
//										Examine remaining bytes
//------------------------------------------^^--------------------------------------------
			for (int i = 1; i < expectedLen; i++) {
				if ((bytes[startPos + i] & 0b11000000) != 0b10000000) {
					character = ".";
					consumes = 1;
					return;
				}
			}
			
//										Copy to shorter array
//------------------------------------------^^--------------------------------------------
			byte[] holdByte = new byte[expectedLen];
			for (int i = 0; i < expectedLen; i++) {
				holdByte[i] = bytes[startPos + i];
			}
			
//										Get Value
//------------------------------------------^^--------------------------------------------
			holdStr = bytesToUtf8(holdByte) + "";
			character = holdStr;
			consumes = expectedLen;
		}
		
//****************************************************************************************
//											bytesToUtf8()
//******************************************^^********************************************
		public String bytesToUtf8(byte[] bytes) {
			String strOut = "";
			try {
				String strIn = new String(bytes, "UTF-8");
				strOut = strIn;
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			return strOut;
		}
		
//****************************************************************************************
//											toBitString()
//******************************************^^********************************************
		public String toBitString(byte inByte) {
			String str;
			
			str = "";
			if ((inByte & 0b10000000) == 0b10000000) { str += "1"; } else {str += "0"; }
			if ((inByte & 0b01000000) == 0b01000000) { str += "1"; } else {str += "0"; }
			if ((inByte & 0b00100000) == 0b00100000) { str += "1"; } else {str += "0"; }
			if ((inByte & 0b00010000) == 0b00010000) { str += "1"; } else {str += "0"; }
			if ((inByte & 0b00001000) == 0b00001000) { str += "1"; } else {str += "0"; }
			if ((inByte & 0b00000100) == 0b00000100) { str += "1"; } else {str += "0"; }
			if ((inByte & 0b00000010) == 0b00000010) { str += "1"; } else {str += "0"; }
			if ((inByte & 0b00000001) == 0b00000001) { str += "1"; } else {str += "0"; }
			
			return str;
		}
			
	}
	
}
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
//									E N D   O F   F I L E
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX--XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
