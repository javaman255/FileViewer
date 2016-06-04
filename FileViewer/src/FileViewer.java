//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
//
//								F i l e V i e w e r
//
//	This is a stand-alone utility which displays the contents of a file in hex and in
//	UTF-8.
//
//	Problems:
//	If the file is less than 256 bytes long, can't position to anything other than 0.
//	Doesn't respond appropriately to resizing the window.
//	Since it is currently read only, should not be able to write in the windows, though
//		want to be able to copy text to the clipboard.
//	"Grey out" navigation buttons until a file is opened.
//	"Go To" is taking way too long.
//
//
//	Enhancements:
//	Add a search function.
//	Add ability to edit the file in hex, ASCII or UTF-8.  Inserts near the beginning of
//		very large files will be slow, don't do it one character at a time.  Have a
//		separate field where you type the text to be entered.  Program can then move every
//		thing up length() bytes at once, rather than once for each byte.
//	Remember the last directory that the user opened and use that for starting point.
//
//	Other Windows:
//	1 byte integer (signed and unsigned)
//	2 byte integer (signed and unsigned)
//	4 byte integer (signed and unsigned)
//	8 byte integer (signed and unsigned)
//	4 byte float
//	8 byte float
//	bit values
//	Unicode Value if UTF-8, etc.
//
//	hex offset in to the file (the "position" in hex.)
//
//	I don't see any reason (other than some dumb language restraint) that any
//		of these should have to begin on any particular byte boundary.  That means a lot 
//		of screen space to display all the possibilities.  Maybe make these various
//		optional windows.  If you know that you are looking for 4 byte addresses, then
//		open the window to display them, otherwise, keep it closed.
//	Check IEEE to see other data formats that are defined especially timestamp or date.
//		If they have defined date, that could be very useful.  When creating a string Java
//		supports "US-ASCII", "ISO-8859-1" (Latin 1), "UTF-8", "UTR1116BE", "UTF-16LE" and
//		"UTF-16".  Option to show bitpattern.
//	Maybe user selects a location in the hex or ascii portion and selects all the ways he
//		wants the data beginning at that address to be displayed.
//
//	A couple things:
//		make variables of the width and height of the display so screen can stretch
//			in the future.
//		Add a read-ahead buffer so that if I read the first byte of a UTF-8 character, I
//			can display it.  Same for displaying int, long, float, etc.
//			Buffer needs to be at least 7 bytes.  If I read an extra rows worth of data,
//			I can pass two rows to the routine and just stop after one.  Means that
//			screen cannot show less than 8 bytes per row.  Also need a read-behind buffer
//			of the same size.  Use the before buffer to get synchronized.  Use the after
//			buffer to finish the last charactter.  First and last lines will be messy to
//			code.
//		Catch UTF-8 errors and display a period ('.').
//		Construct UTF-8 display one character at a time.  Keep track of buffer position
//			and display position separately, show the value in the position of the first
//			byte making up the character while increment display by only one position
//			regardless of the number of bytes consumed from the buffer.  Ie, in Chinese
//			text, for instance, I should  not display the character followed by three 
//			periods just because the character takes 4 bytes to store.
//			characters in the utf-8 window to be up against each other, not spaced out
//			across the line.  But characters should be displayed on the same "line" as
//			the corresponding hex, so there is a chance to find your way back and forth.
//			Selecting a utf-8 character could highlight the corresponding hex display.
//		Using graphics to print the UTF-8.  I couldn't find a font that was, free,
//			universal, monospace and contained the majority of the unicode characters.  I
//			used Graphics in order to emulate a monospaced font.  That gave me a lot of
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
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.LayoutStyle.ComponentPlacement;

public class FileViewer extends JFrame {
	private JPanel contentPane;
	private JTextField jFileName;
	private JTextArea taPosition = new JTextArea();
	private JTextArea taHex = new JTextArea();
	private JTextField tfAddress;

	private RandomAccessFile raf = null;
	private int requestCols = 16;
	private int requestRows = 16;
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
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

//****************************************************************************************
//										FileViewer()
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
		setBounds(100, 100, 1055, 450);
		contentPane = new JPanel();
		contentPane.setAutoscrolls(true);
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);

		JPanel pnlFile = new JPanel();

//****************************************************************************************
//										btnFileDlg
//******************************************^^********************************************
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
//										btnOpen
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
				requestPos = 0;
				display();
			}
		});
		pnlFile.add(btnOpen);

		JPanel pnlNav = new JPanel();
		pnlNav.setLayout(new GridLayout(0, 8, 0, 0));

//----------------------------------------------------------------------------------------
//										tfAddress
//------------------------------------------^^--------------------------------------------
		tfAddress = new JTextField();
		tfAddress.setFont(new Font("Courier New", Font.PLAIN, 12));
		pnlNav.add(tfAddress);
		tfAddress.setColumns(12);

//----------------------------------------------------------------------------------------
//										btnGoTo
//------------------------------------------^^--------------------------------------------
		JButton btnGoTo = new JButton("Go To");
		btnGoTo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				long lngAddr;
				String strAddr = "";
				strAddr = tfAddress.getText();
				strAddr = strAddr.trim();
				strAddr = strAddr.replace(",", "");
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
		});
		pnlNav.add(btnGoTo);

//----------------------------------------------------------------------------------------
//										btnStart
//------------------------------------------^^--------------------------------------------
		JButton btnStart = new JButton("|<");
		btnStart.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				requestPos = 0;
				display();
			}
		});
		pnlNav.add(btnStart);

//----------------------------------------------------------------------------------------
//										btnPageUp
//------------------------------------------^^--------------------------------------------
		JButton btnPageUp = new JButton("<<");
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
//										btnLineUp
//------------------------------------------^^--------------------------------------------
		JButton btnLineUp = new JButton("<");
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
//										btnLineDown
//------------------------------------------^^--------------------------------------------
		JButton btnLineDown = new JButton(">");
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
//										btnPageDown
//------------------------------------------^^--------------------------------------------
		JButton btnPageDown = new JButton(">>");
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
//										btnEnd
//------------------------------------------^^--------------------------------------------
		JButton btnEnd = new JButton(">|");
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
		JPanel pnlPostion = new JPanel();
		pnlPostion.setLayout(new BorderLayout(2, 2));
		JLabel lblPosition = new JLabel("Position");
		pnlPostion.add(lblPosition, BorderLayout.NORTH);
		taPosition.setFont(new Font("Courier New", Font.PLAIN, fntSzHex));
		taPosition.setRows(requestRows);
		taPosition.setColumns(12);
		pnlPostion.add(taPosition);

//----------------------------------------------------------------------------------------
//											hex
//------------------------------------------^^--------------------------------------------
		JPanel pnlHex = new JPanel();
		pnlHex.setLayout(new BorderLayout(2, 2));
		JLabel lblHex = new JLabel("Hex");
		pnlHex.add(lblHex, BorderLayout.NORTH);
		taHex.setFont(new Font("Courier New", Font.PLAIN, fntSzHex));
		taHex.setRows(requestRows);
		taHex.setColumns(54);
		pnlHex.add(taHex);
		
//----------------------------------------------------------------------------------------
//		utf-8
//------------------------------------------^^--------------------------------------------
		Container pnlGraph = new Container();
		pnlGraph.setLayout(new BorderLayout(2, 2));
	    cmpUtf8.setFont(fntUtf8);
	    pnlGraph.setBackground(Color.WHITE);;
	    pnlGraph.add(cmpUtf8);
	    
//----------------------------------------------------------------------------------------
//		Horizontal
//------------------------------------------^^--------------------------------------------
		GroupLayout gl_contentPane = new GroupLayout(contentPane);
		gl_contentPane.setHorizontalGroup(
			gl_contentPane.createParallelGroup(Alignment.LEADING)
				.addGroup(gl_contentPane.createSequentialGroup()
					.addGroup(gl_contentPane.createParallelGroup(Alignment.LEADING)
						.addGroup(gl_contentPane.createSequentialGroup()
							.addContainerGap()
							.addGroup(gl_contentPane.createParallelGroup(Alignment.LEADING)
								.addComponent(pnlNav, GroupLayout.DEFAULT_SIZE, 857, Short.MAX_VALUE)
								.addGroup(gl_contentPane.createSequentialGroup()
									.addComponent(pnlPostion, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
									.addPreferredGap(ComponentPlacement.UNRELATED)
									.addComponent(pnlHex, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
									.addGap(10)
									.addComponent(pnlGraph, GroupLayout.DEFAULT_SIZE, 331, Short.MAX_VALUE))))
						.addComponent(pnlFile, GroupLayout.DEFAULT_SIZE, 897, Short.MAX_VALUE))
					.addContainerGap())
		);
//----------------------------------------------------------------------------------------
//		Vertical
//------------------------------------------^^--------------------------------------------
		gl_contentPane.setVerticalGroup(
			gl_contentPane.createParallelGroup(Alignment.LEADING)
				.addGroup(gl_contentPane.createSequentialGroup()
					.addComponent(pnlFile, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addGroup(gl_contentPane.createParallelGroup(Alignment.LEADING)
						.addGroup(gl_contentPane.createSequentialGroup()
							.addGap(5)
							.addGroup(gl_contentPane.createParallelGroup(Alignment.LEADING)
								.addComponent(pnlHex, GroupLayout.DEFAULT_SIZE, 330, Short.MAX_VALUE)
								.addComponent(pnlPostion, GroupLayout.DEFAULT_SIZE, 330, Short.MAX_VALUE)))
						.addGroup(gl_contentPane.createSequentialGroup()
							.addGap(23)
							.addComponent(pnlGraph, GroupLayout.DEFAULT_SIZE, 312, Short.MAX_VALUE)))
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addComponent(pnlNav, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
		);
		
		contentPane.setLayout(gl_contentPane);
	}

//****************************************************************************************
//										display()
//******************************************^^********************************************
	public void display() {
		int lineCnt = 0;
		DecimalFormat formatter = new DecimalFormat("#,##0");
		long dispPos;
		long inPos;
		int preBuffLen;
		String strPos = "";
		String strHex = "";
//		int mainArrLen;
		
		readPos = Math.max(0, requestPos - buffLen);
		preBuffLen = (int) (requestPos - readPos);
		readLen = preBuffLen + requestPage + buffLen;
		if (readLen + readPos > rafLen) {
			readLen = rafLen - readPos;
		}
//		readLen = Math.min((requestCols * requestRows) + (buffLen * 2), (rafLen - requestPos) );
//		mainArrLen = (int) Math.min(readLen, rafLen);
		byte[] rafArray = new byte[(int) readLen];
		byte[] rowBytArr = new byte[requestCols];

//----------------------------------------------------------------------------------------
//								Initialize accumUtf8 array
//------------------------------------------^^--------------------------------------------
		for(int i = 0; i < requestRows; i++) {
			strUtf8[i] = "";
		}

//----------------------------------------------------------------------------------------
//										Read File
//------------------------------------------^^--------------------------------------------
		rafArray = fileReader(raf, rafArray);
		dispPos = requestPos;
		inPos = 0;
		
//----------------------------------------------------------------------------------------
//								Format strings for display
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
//							Format strings for UTF-8  Display
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
//			String a;
//			a = showUsYourBits(s);
//			if ((uc.consumes > 1) && (a.equalsIgnoreCase("0011 1111"))) {
//				s = ".";
//			}
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
//							move strings to the output screen
//------------------------------------------^^--------------------------------------------
		taPosition.setText(strPos);
		taPosition.paintImmediately(0, 0, taPosition.getWidth(), taPosition.getHeight());
		taHex.setText(strHex);
		taHex.paintImmediately(0, 0, taHex.getWidth(), taHex.getHeight());
		cmpUtf8.repaint();

	}

//****************************************************************************************
//										fileReader()
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
//										bytesToHex()
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
//										isInteger()
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
//											arrayPiece()
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
//										dspBits()
//	Constructs a String of 1's and 0's representing the bit pattern of the object.
//	Accepts byte, char, int and long.
//	Restrictions on which types can be used with bitwise operators prevents other object
//		types from being available.  In particular, String, float, double and Object.
//	I reverse the bytes because Intel has their expletive endians messed up.
//  Maybe it's not an endian problem.  Can'g get any value from second byte.
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
			byte[] ba;
			ba = inString.getBytes();
			tempStr = "";
			for (int byteCnt = 0; byteCnt < ba.length; byteCnt++) {
				inByte = ba[byteCnt];
				for (int i = 0; i < 8; i++) {
					if ((inByte & 0b10000000) == 0b10000000) { str += "1"; } else { str += "0"; }
					inByte = (byte) (inByte << 1);
				}
				tempStr += "00000000";
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
//										MyCanvas()
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX^^XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
	class GraphicsArea extends JComponent {
		@Override
		public void paintComponent(Graphics g) {
			String tempStr;
			int left, top, width, height;
			left = 8;
			top = -4;
			width = (fntSzUtf8 + 2) * requestCols;
			height = (fntSzUtf8 + 2) * requestRows;
			g.setColor(Color.WHITE);
			g.fillRect(left, top, width, height);
			g.setColor(Color.BLACK);
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
//									U t f 8 C h a r
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX^^XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
	class Utf8Char{
		String character;
		int consumes;
		
//****************************************************************************************
//										Utf8Char
//******************************************^^********************************************
		public Utf8Char(byte[] bytes, int startPos) {
			int expectedLen;
			String holdStr;
			
			String display;
			display = toBitString(bytes[startPos]);
			
//										Get Length
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
			
//									Examine remaining bytes
//------------------------------------------^^--------------------------------------------
			for (int i = 1; i < expectedLen; i++) {
				if ((bytes[startPos + i] & 0b11000000) != 0b10000000) {
					character = ".";
					consumes = 1;
					return;
				}
			}
			
//									Copy to shorter array
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
//										bytesToUtf8()
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
//										toBitString()
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
