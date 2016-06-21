

import java.io.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Writer;

public class FileWriter {

	public static void main(String[] args) {
		RandomAccessFile raf1 = null;
		RandomAccessFile raf2 = null;
		RandomAccessFile raf3 = null;

		String toFile1 = "test_8_bit.txt";
		String toFile2 = "test_16_bit.txt";
		String toFile3 = "test_UTF_8.txt";
		try {
			raf1 = new RandomAccessFile(toFile1, "rw");
			raf2 = new RandomAccessFile(toFile2, "rw");
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
		FileWriter(raf1, raf2, raf3);
		
		writeUtf8(toFile3);
	}

//****************************************************************************************
//										filewriter()
//
//	File 1 gets 256 one byte values yielding 256 bytes in file.
//	File 2 gets 65,536 two byte values yelding 131,072 bytes totla.
//
//******************************************^^********************************************
	public static void FileWriter(RandomAccessFile raf1, RandomAccessFile raf2, RandomAccessFile raf3) {
//		byte b[] = new byte[131072];
		int i = 0;
		int j = 0;

		try {
			for (i = 0x00; i < 0x0100; i++) {
				raf1.write(i);
				for (j = 0x00; j < 0x0100; j++) {
					raf2.write(i);
					raf2.write(j);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
//****************************************************************************************
//		writeUff8()
//
//	Seems to me that byte 128 should be "C080" not "LC280" otherwise...
//	maybe it just knows that there aren't any valid values in that range?
//	3968 should be "E08080" instead it is "E0A080".  
//
//******************************************^^********************************************
	static void writeUtf8(String fileName) {
		int i;
		
		try {
			Writer out = new BufferedWriter(new OutputStreamWriter(
				    new FileOutputStream(fileName), "UTF-8"));
				try {
					for (i = 0; i < 1112064; i++) {
						out.write(i);
					}
				} finally {
				    out.close();
				}
		
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	////End UTF file
	}

}
	
