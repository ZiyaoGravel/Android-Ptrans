package MBUS;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;


class SerialTransceiver {

	private final String serialPortName;
	private final int baudRate;
	private final int dataBits;
	private final int stopBits;
	private final int parity;
	private DataOutputStream os;
	private DataInputStream is;
	private SerialPort serialPort;
	
	
	public SerialTransceiver(String serialPortName, int baudRate, int dataBits, int stopBits, int parity) {
		this.serialPortName = serialPortName;
		this.baudRate = baudRate;
		this.dataBits = dataBits;
		this.stopBits = stopBits;
		this.parity = parity;
	}

	/**
	 * Opens the serial port. The serial port needs to be opened before attempting to read a device.
	 * 
	 * @throws IOException
	 *             if any kind of error occurs opening the serial port.
	 */
	public void open() throws IOException {
		CommPortIdentifier.getPortIdentifiers();
		CommPortIdentifier portIdentifier;
		try {
			portIdentifier = CommPortIdentifier.getPortIdentifier(serialPortName);
		} catch (NoSuchPortException e) {
			throw new IOException("Serial port with given name \"" + serialPortName + "\" does not exist", e);
		}

		if (portIdentifier.isCurrentlyOwned()) {
			throw new IOException("Serial port is currently in use.");
		}

		CommPort commPort;
		try {
			commPort = portIdentifier.open(this.getClass().getName(), 2000);
		} catch (PortInUseException e) {
			throw new IOException("Serial port is currently in use.", e);
		}

		if (!(commPort instanceof SerialPort)) {
			commPort.close();
			throw new IOException("The specified CommPort is not a serial port");
		}

		serialPort = (SerialPort) commPort;

		try {
			serialPort.setSerialPortParams(baudRate, dataBits, stopBits, parity);
		} catch (UnsupportedCommOperationException e) {
			serialPort.close();
			serialPort = null;
			throw new IOException("Unable to set the baud rate or other serial port parameters", e);
		}

		try {
			os = new DataOutputStream(serialPort.getOutputStream());
			is = new DataInputStream(serialPort.getInputStream());
		} catch (IOException e) {
			serialPort.close();
			serialPort = null;
			throw new IOException("Error getting input or output or input stream from serial port", e);
		}

	}

	/**
	 * Closes the serial port.
	 */
	public void close() {
		if (serialPort == null) {
			return;
		}
		serialPort.close();
		serialPort = null;
	}

	public DataOutputStream getOutputStream() {
		return os;
	}

	public DataInputStream getInputStream() {
		return is;
	}

	public boolean isClosed() {
		return (serialPort == null);
	}
}
