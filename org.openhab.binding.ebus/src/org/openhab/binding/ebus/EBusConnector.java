package org.openhab.binding.ebus;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Dictionary;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.TooManyListenersException;
import java.util.concurrent.LinkedBlockingQueue;

import org.json.simple.parser.ParseException;
import org.openhab.binding.ebus.parser.EBusTelegramParser;
import org.openhab.binding.ebus.serial.EBusSerialPortEvent;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.State;
import org.osgi.service.cm.ConfigurationException;
//import org.openhab.core.library.types.DecimalType;
//import org.openhab.core.types.PrimitiveType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EBusConnector {

	private static final Logger logger = LoggerFactory
			.getLogger(EBusConnector.class);
	
	private Queue<EbusTelegram> outputQueue = new LinkedBlockingQueue<EbusTelegram>(20);
	
	/** serial connection if open */
	private SerialPort serialPort;
	
	/**
	 * Open a connection to listen on the ebus (serial) for telegrams. A separate 
	 * event thread processes the received telegrams.
	 * @param eBusBinding
	 * @param properties The properties with information for serial port etc.
	 * @throws NoSuchPortException
	 * @throws PortInUseException
	 * @throws IOException
	 * @throws ParseException
	 * @throws ConfigurationException
	 */
	public void open(final EBusBinding eBusBinding, Dictionary<String, ?> properties) throws NoSuchPortException, PortInUseException, IOException, ParseException, ConfigurationException {

		
		if(properties == null) {
			throw new ConfigurationException("global", "No properties in openhab.cfg defined!");
		}
		
		String port = (String) properties.get("serialPort");
		if(port == null || port.equals("")) {
			throw new ConfigurationException("serialPort", "sadasd");
		}
		
		try {
			CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(port);
			
			serialPort = (SerialPort) portIdentifier.open("openhab-ebus", 3000);
			serialPort.setSerialPortParams(2400, SerialPort.DATABITS_8,
					SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

			serialPort.disableReceiveTimeout();
			serialPort.enableReceiveThreshold(1);

			final EBusTelegramParser parser = new EBusTelegramParser();
			URL configurationUrl = this.getClass().getResource("/META-INF/ebus-configuration.json");

			// check customized parser url
			String parserUrl = (String) properties.get("parserUrl");
			if(parserUrl != null) {
				logger.info("Use custom parser with url {}", parserUrl);
				configurationUrl = new URL(parserUrl);
			}
			
			parser.loadConfigurationFile(configurationUrl);
			
			EBusSerialPortEvent event = new EBusSerialPortEvent(serialPort) {
				@Override
				public void onEBusTelegramAvailable(EbusTelegram telegram) {
					Map<String, Object> results = parser.parse(telegram);
					if(results != null) {
						for (Entry<String, Object> entry : results.entrySet()) {
							
							State state = null;
							if(entry.getValue() instanceof Float) {
								state = new DecimalType((Float)entry.getValue());
							} else if(entry.getValue() instanceof Double) {
								state = new DecimalType((Double)entry.getValue());
							} else if(entry.getValue() instanceof Integer) {
									state = new DecimalType((Integer)entry.getValue());
							} else if(entry.getValue() instanceof Byte) {
								state = new DecimalType((Byte)entry.getValue());
							} else if(entry.getValue() instanceof Boolean) {
								state = (boolean)entry.getValue() ? OnOffType.ON : OnOffType.OFF;
							} else if(entry.getValue() instanceof String) {
								state = new StringType((String)entry.getValue());
							} else if(entry.getValue() == null) {
								// noop
							} else {
								logger.error("Unknwon data type!");
							}
							
							if(state != null) {
								eBusBinding.postUpdate(entry.getKey(), state);
							}
						}
					};
					
				}
			};
			
			// setz events
			serialPort.addEventListener(event);
			serialPort.notifyOnDataAvailable(true);
			serialPort.notifyOnOutputEmpty(true);
			
			logger.debug("EBus Connector communication running ...");
			
		} catch (TooManyListenersException e) {
			logger.error(e.toString(), e);
		} catch (UnsupportedCommOperationException e) {
			logger.error(e.toString(), e);
		}
	}

	public void send(EbusTelegram telegram) {
		try {
			outputQueue.add(telegram);
			try {
				OutputStream outputStream = serialPort.getOutputStream();
				outputStream.write(null);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//			serialPort.getOutputStream().
		} catch (IllegalStateException e) {
			throw new IllegalStateException("Unable to send more telegram, send queue is full");
		}
	}
	
	/**
	 * Closes the connector
	 */
	public void close() {
		logger.debug("Close EBus Connector ...");
		if(serialPort != null) {
			serialPort.close();
		}
	}

	/**
	 * Check if the connector is open
	 * @return true if the connector is open
	 */
	public boolean isOpen() {
		return serialPort != null;
	}
}