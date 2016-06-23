/* XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
 *
 *								F i l e V i e w e r
 *
 *	This is a stand-alone utility which displays the contents of a file in hex and in
 *	UTF-8.
 *
 *	Fixed:
 *	Handle "File Not Found" condition
 *	Provide a success / failure message on open
 *	Clear out the display when you go to open a new file.
 *	Display file length.
 *
 *	Bugs to fix:
 *	The File buttons have a different style than the navigation buttons.
 *
 *	Enhancements to make:
 *	Add support for UTF-16, etc.
 *	Display file size.
 *	Add ability to resize window and the components will all resize appropriately.
 *	Grey out the unused right hand portion of the UTF-8 panel.
 *	Allow user to select a font.
 *	Support a .ini file to store user's choices.
 *	Remember the last directory that the user opened and use that for starting point.
 *	Add ability to select text from the screen.
 *	Add hex offset in to the file (the "position" in hex.)
 *	ALlow to enter hex address in the Go To field.
 *	Catch UTF-8 errors and display a period ('.').
 *	Add a search function.
 *	Add ability to edit the file in hex, ASCII or UTF-8.  Inserts near the beginning of
 *		very large files will be slow, don't do it one character at a time.  Have a
 *		separate field where you type the text to be entered.  Program can then move every
 *		thing up length() bytes at once, rather than once for each byte.
 *
 *	Other Windows:
 *	1 byte integer (signed and unsigned)
 *	2 byte integer (signed and unsigned)
 *	4 byte integer (signed and unsigned)
 *	8 byte integer (signed and unsigned)
 *	4 byte float
 *	8 byte float
 *	Bit values
 *	Unicode Value if UTF-8, etc.
 *
 *
 *	I don't see any reason (other than some dumb language restraint) that any
 *		of these should have to begin on any particular byte boundary.  That means a lot 
 *		of screen space to display all the possibilities.  Maybe make these various
 *		optional windows.  If you know that you are looking for 4 byte addresses, then
 *		open the window to display them, otherwise, keep it closed.
 *	Check IEEE to see other data formats that are defined especially timestamp or date.
 *		If they have defined date, that could be very useful.  When creating a string Java
 *		supports "US-ASCII", "ISO-8859-1" (Latin 1), "UTF-8", "UTR1116BE", "UTF-16LE" and
 *		"UTF-16".
 *	Maybe user selects a location in the hex or ascii portion and selects all the ways he
 *		wants the data beginning at that address to be displayed.
 *
 *	Comment:
 *		Using graphics to print the UTF-8.  I couldn't find a font that was, free,
 *			universal, monospace and contained the majority of the unicode characters.  I
 *			used Graphics in order to emulate a monospaced font.  That gave me more
 *			freedom in choosing the font.
 *
 *		See http: *www.programcreek.com/java-api-examples/java.nio.charset.CharsetDecoder
 *			for examples for decoding utf-8
 *
 *	copyright 2013-2016 by William Rice
 * XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX--XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
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
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.Component;

/* XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
 *										FileViewer
 * XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX--XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX */
public class FileViewer extends JFrame {

	private JPanel contentPane;
	private JTextField tfFileName;
	private JTextArea taPosition = new JTextArea();
	private JTextArea taHex = new JTextArea();
	private JTextField tfAddress;
	
	private JButton btnGoTo = new JButton("Go To");
	private JButton btnStart = new JButton("|<");
	private JButton btnPageUp = new JButton("<<");
	private JButton btnLineUp = new JButton("<");
	private JButton btnLineDown = new JButton(">");
	private JButton btnPageDown = new JButton(">>");
	private JButton btnEnd = new JButton(">|");

	private RandomAccessFile raf = null;
	private int requestCols = 16;
	private int requestRows = 16;
	private long requestPos;
	
	private long readPos;
	private long readLen;
	private long rafLen;
	private int buffLen = 3;
	private int fntSzUtf8 = 18;
	private int fntSzHex = 16;
	private int requestPage = requestCols * requestRows;
	private Font fntUtf8 = new Font("Lucida", Font.PLAIN, fntSzUtf8);
	private String[] strUtf8 = new String[requestRows];
	private GraphicsArea cmpUtf8 = new GraphicsArea();
	private boolean blankItOut;
	private DecimalFormat formatter = new DecimalFormat("#,##0");

/* ***************************************************************************************
 *											main
 * *****************************************^^***************************************** */
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

/* ***************************************************************************************
 *										FileViewer()
 * *****************************************^^***************************************** */
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
		
/* ==================================================================================== */
		JPanel pnlFile = new JPanel();
		contentPane.add(pnlFile, BorderLayout.NORTH);
		
/* ---------------------------------------------------------------------------------------
 *										btnFileDlg
 * -----------------------------------------^^----------------------------------------- */
		
/* ---------------------------------------------------------------------------------------
 *										btnOpen
 * -----------------------------------------^^----------------------------------------- */
		pnlFile.setLayout(new BorderLayout(0, 0));
		
		JPanel panel_1 = new JPanel();
		pnlFile.add(panel_1, BorderLayout.NORTH);
		JButton btnFileDlg = new JButton("Browse...");
		panel_1.add(btnFileDlg);
		
		JLabel lblNewLabel = new JLabel("File Name");
		panel_1.add(lblNewLabel);
		
		tfFileName = new JTextField();
		panel_1.add(tfFileName);
		tfFileName.setColumns(30);
		JButton btnOpen = new JButton("Open");
		panel_1.add(btnOpen);
		
		JPanel panel_2 = new JPanel();
		pnlFile.add(panel_2, BorderLayout.SOUTH);
		
		JLabel lblOpenMsg = new JLabel("   ");
		panel_2.add(lblOpenMsg);
		lblOpenMsg.setHorizontalAlignment(SwingConstants.CENTER);
		lblOpenMsg.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		JLabel lblFileLength = new JLabel("   ");
		lblFileLength.setHorizontalAlignment(SwingConstants.CENTER);
		lblFileLength.setAlignmentX(0.5f);
		panel_2.add(lblFileLength);
		btnOpen.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String fromFile = tfFileName.getText();
				if (fromFile.isEmpty()) {
					return;
				}
				try {
					raf = new RandomAccessFile(fromFile, "r");
					rafLen = raf.length();
					lblOpenMsg.setForeground(Color.BLACK);
					lblOpenMsg.setText("Open Successful");
					lblFileLength.setText(" --    File length = " + formatter.format(rafLen));
					blankItOut = false;
				} catch (IOException e1) {
					rafLen = 0;
					lblOpenMsg.setForeground(Color.RED);
					lblOpenMsg.setText("Open Failed");
					lblFileLength.setText("");
					blankItOut = true;
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
		btnFileDlg.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				final JFileChooser fileDialog = new JFileChooser();
				fileDialog.setApproveButtonText("Select");
				fileDialog.showOpenDialog(contentPane);
				File openFile = fileDialog.getSelectedFile();
				if (openFile != null) {
					String path = openFile.getPath();
					tfFileName.setText(path);
				}
			}
		});
		
/* ==================================================================================== */
		JPanel pnlNav = new JPanel();
		contentPane.add(pnlNav, BorderLayout.SOUTH);
		
/* ---------------------------------------------------------------------------------------
 *										tfAddress
 * -----------------------------------------^^----------------------------------------- */
		tfAddress = new JTextField();
		tfAddress.setToolTipText("Integer to move to specific position");
		tfAddress.setFont(new Font("Courier New", Font.PLAIN, 12));
		pnlNav.add(tfAddress);
		tfAddress.setColumns(12);
		
/* ---------------------------------------------------------------------------------------
 *										btnGoTo
 * -----------------------------------------^^----------------------------------------- */
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
		
/* ---------------------------------------------------------------------------------------
 *										btnStart
 * -----------------------------------------^^----------------------------------------- */
		btnStart.setToolTipText("Beginning");
		btnStart.setEnabled(false);
		btnStart.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				requestPos = 0;
				display();
			}
		});
		pnlNav.add(btnStart);
		
/* ---------------------------------------------------------------------------------------
 *										btnPageUp
 * -----------------------------------------^^----------------------------------------- */
		btnPageUp.setToolTipText("Page Back");
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
		
/* ---------------------------------------------------------------------------------------
 *										btnLineUp
 * -----------------------------------------^^----------------------------------------- */
		btnLineUp.setToolTipText("Line Back");
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
		
/* ---------------------------------------------------------------------------------------
 *										btnLineDown
 * -----------------------------------------^^----------------------------------------- */
		btnLineDown.setToolTipText("Line Forward");
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
		
/* ---------------------------------------------------------------------------------------
 *										btnPageDown
 * -----------------------------------------^^----------------------------------------- */
		btnPageDown.setToolTipText("Page Forward");
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
		
/* ---------------------------------------------------------------------------------------
 *										btnEnd
 * -----------------------------------------^^----------------------------------------- */
		btnEnd.setToolTipText("End");
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
		
/* ---------------------------------------------------------------------------------------
 *										position
 * -----------------------------------------^^----------------------------------------- */
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
		
/* ==================================================================================== */
		JPanel pnlData = new JPanel();
		contentPane.add(pnlData, BorderLayout.CENTER);
		pnlData.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
		
/* ---------------------------------------------------------------------------------------
 *											hex
 * -----------------------------------------^^----------------------------------------- */
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
		
/* ---------------------------------------------------------------------------------------
 *										pnlUtf8
 * -----------------------------------------^^----------------------------------------- */
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

/* ***************************************************************************************
 *											display()
 * *****************************************^^***************************************** */
	public void display() {
		int lineCnt = 0;
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

/* ---------------------------------------------------------------------------------------
 *									Initialize accumUtf8 array
 * -----------------------------------------^^----------------------------------------- */
		for(int i = 0; i < requestRows; i++) {
			strUtf8[i] = "";
		}

/* ---------------------------------------------------------------------------------------
 *										Read File
 * -----------------------------------------^^----------------------------------------- */
		rafArray = fileReader(raf, rafArray);
		dispPos = requestPos;
		
/* ---------------------------------------------------------------------------------------
 *								Format strings for display
 * -----------------------------------------^^----------------------------------------- */
		inPos = 0;
		for (lineCnt = 0; lineCnt < requestRows; lineCnt++) {
			List<Byte> rowByLst = new ArrayList<Byte>();
			for (int i = 0; i < requestCols; i++) {
				if (inPos +  i < readLen) {
					rowByLst.add(rafArray[(int)inPos + i]);
				}
			}
			if (dispPos < rafLen) {
				strPos += "" + formatter.format(dispPos) + "\n";
			}
			byte[] rowByArr = new byte[rowByLst.size()];
			for (int i = 0; i < rowByArr.length; i++) {
				rowByArr[i] = rowByLst.get(i);
			}
			strHex += bytesToHex(rowByArr) + "\n";
			dispPos += requestCols;
			inPos += requestCols;
		}
		if (strPos.length() > 1) {
			strPos = strPos.substring(0, strPos.length() - 1);
		}
		strHex = strHex.substring(0, strHex.length() - 1);
		
/* ---------------------------------------------------------------------------------------
 *							Format strings for UTF-8  Display
 * -----------------------------------------^^----------------------------------------- */
		int dspIdx = 0;
		String holdStr = "";
		String s = "";
		lineCnt = 0;
		int readIdx;
  		for (readIdx = 0; readIdx < rafArray.length; ) {
			Utf8Char uc = new Utf8Char(rafArray, readIdx);
			if (readIdx < preBuffLen) {
				readIdx += uc.consumes;
				continue;
			}
			readIdx += uc.consumes;
			s = uc.character;
			dspIdx = readIdx - preBuffLen;
			holdStr += s;
			if (dspIdx < ((lineCnt + 1) * requestCols)) { continue; }
			if (lineCnt < requestRows) {
				strUtf8[lineCnt] = holdStr;
				holdStr = "";
			}
			lineCnt++;
		}
  		if ((lineCnt < requestCols) && (holdStr.length()) > 0) {
			strUtf8[lineCnt] = holdStr;
  		}
		
/* ---------------------------------------------------------------------------------------
 *							move strings to the output screen
 * -----------------------------------------^^----------------------------------------- */
		taPosition.setText(strPos);
		taPosition.paintImmediately(0, 0, taPosition.getWidth(), taPosition.getHeight());
		taHex.setText(strHex);
		taHex.paintImmediately(0, 0, taHex.getWidth(), taHex.getHeight());
		cmpUtf8.repaint();

	}

/* ***************************************************************************************
 *										fileReader()
 * *****************************************^^***************************************** */
	public  byte[] fileReader(RandomAccessFile raf, byte[] inArray) {
		long len1 = 0;
		long len2 = 0;
		len1 = Math.min(inArray.length, rafLen - readPos);
		byte[] outArray = new byte[(int) len1];
		
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

/* ***************************************************************************************
 *										bytesToHex()
 * *****************************************^^***************************************** */
	public static String bytesToHex(byte[] bytes) {
		final char[] hexArray = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
		char[] hexChars = new char[bytes.length * 2];
		int v;
		int j;
		for (j = 0; j < bytes.length; j++) {
			v = bytes[j] & 0xFF;				// otherwise, it treats the byte as signed
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[(j * 2) + 1] = hexArray[v & 0x0F];
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
		if ((str2.length() > 2)  && (str2.substring(str2.length() - 3, str2.length()) == " . ")) {
			str2 = str2.substring(0, str2.length() - 3);
		}
		return str2;
	}
	
/* ***************************************************************************************
 *										isInteger()
 * *****************************************^^***************************************** */
	public static boolean isInteger(String str) {
		try {
			int d = Integer.parseInt(str);
		} catch (NumberFormatException nfe) {
			return false;
		}
		return true;
	}
	
/* XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
 *										DrawPanel
 * XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX^^XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX */
	public class GraphicsArea extends JPanel {
/* ***************************************************************************************
 *										GraphicsArea()
 * *****************************************^^***************************************** */
		public GraphicsArea() {							// set up graphics window
			super();
			}
		
/* ***************************************************************************************
 *										paintComponent()
 * *****************************************^^***************************************** */
		public void paintComponent(Graphics g) {		// draw graphics in the panel
			int left, top, width, height;
			left = 0;
			top = -4;
			width = getWidth();						// width of window in pixels
			height = getHeight();					// height of window in pixels
			height = (fntSzUtf8 + 2) * requestRows;
			width = (fntSzUtf8 + 2) * requestCols;
			g.fillRect(left, top, width, height);
			super.paintComponent(g);				// call superclass to make panel display correctly
			cmpUtf8.setForeground(Color.BLACK);
			setBackground(Color.WHITE);

			if (blankItOut == true) {
				return;
			}
			
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
	
/* XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
 *										U t f 8 C h a r
 * XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX^^XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX */
	class Utf8Char{
		String character;
		int consumes;
		
/* ***************************************************************************************
 *										Utf8Char
 * *****************************************^^***************************************** */
		public Utf8Char(byte[] bytes, int startPos) {
			int expectedLen;
			String holdStr;

/*										Get Length
 * -----------------------------------------^^----------------------------------------- */
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
			
/*								Examine remaining bytes
 * -----------------------------------------^^----------------------------------------- */
			for (int i = 1; i < expectedLen; i++) {
				if ((bytes[startPos + i] & 0b11000000) != 0b10000000) {
					character = ".";
					consumes = 1;
					return;
				}
			}
			
/*									Copy to shorter array
 * -----------------------------------------^^----------------------------------------- */
			byte[] holdByte = new byte[expectedLen];
			for (int i = 0; i < expectedLen; i++) {
				holdByte[i] = bytes[startPos + i];
			}
			
/*										Get Value
 * -----------------------------------------^^----------------------------------------- */
			holdStr = bytesToUtf8(holdByte) + "";
			character = holdStr;
			consumes = expectedLen;
		}
		
/* ***************************************************************************************
 *										bytesToUtf8()
 * *****************************************^^***************************************** */
		public String bytesToUtf8(byte[] bytes) {
			String strOut = "";
			try {
				strOut = new String(bytes, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			return strOut;
		}
			
	}
	
}
/* XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
 *									E N D   O F   F I L E
 * XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX^^XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX */
