package org.entermediadb.asset.convert.transcoders;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.entermediadb.asset.Asset;
import org.entermediadb.asset.convert.BaseTranscoder;
import org.entermediadb.asset.convert.ConvertInstructions;
import org.entermediadb.asset.convert.ConvertResult;
import org.json.simple.JSONObject;
import org.openedit.repository.ContentItem;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;

public class RabbitMQTranscoder extends BaseTranscoder
{
	private static final Log log = LogFactory.getLog(RabbitMQTranscoder.class);
	
	public ConvertResult convert(ConvertInstructions inStructions)
	{
		//The end result
		ConvertResult result = new ConvertResult();
		
		//Set the output file
		result.setOutput(inStructions.getOutputFile());
		
		//Get the inAsset
		Asset inAsset = inStructions.getAsset();
		
		//Bail if the file is not here...
		ContentItem inputpage = inStructions.getInputFile();
		if (inputpage == null || !inputpage.exists())
		{
			//no such original
			log.info("Original does not exist: " + inStructions.getAsset().getSourcePath());
			result.setOk(false);
			return result;
		}
		
		//Create the output path
		String outpath = inStructions.getOutputFile().getAbsolutePath();
		new File(outpath).getParentFile().mkdirs();
		
		//Create a structured message
		JSONObject json = new JSONObject();
		
		//Input file
		json.put("input", inputpage.getAbsolutePath().toString());
		
		//Output File
		json.put("output", inStructions.getOutputFile().getAbsolutePath().toString());
		
		//Asset Id file
		json.put("assetid", inAsset.getId().toString());
		
		//Preset Id
		json.put("presetid", inStructions.getConvertPreset().getId());
		
		//Create the message string
		String message = json.toString();
		log.info("RabbitMQ message : " + message);

		//Get the queue name from the preset
		String queue = inStructions.get("queue");
		log.info("RabbitMQ queue : " + queue);
		
		//Create a new connection factory
		ConnectionFactory factory = new ConnectionFactory();
		
		//Set the host, this needs to be dynamic
		//Does this include the port?
		factory.setHost(inStructions.get("ip"));
		factory.setPort(inStructions.intValue("port", 5672));
		
		factory.setUsername(inStructions.get("username"));
		factory.setPassword(inStructions.get("password"));
		factory.setVirtualHost(inStructions.get("vhost"));
		
		log.info("RabbitMQ host : " + inStructions.get("ip"));
		log.info("RabbitMQ port : " + inStructions.intValue("port", 5672));
		log.info("RabbitMQ user : " + inStructions.get("username"));
		log.info("RabbitMQ vhost : " + inStructions.get("vhost"));
		
		
		//Create a new connection
		Connection connection = null;
		try {
			connection = factory.newConnection();
			log.info("RabbitMQ create connection interface");
		} catch (IOException | TimeoutException e) {
			
			e.printStackTrace();
			log.error("RabbitMQ Failed to create connection interface");
			result.setOk(false);
			return result;
		}
		
		//Create a message channel
		Channel channel = null;
		try {
			channel = connection.createChannel();
			log.info("RabbitMQ create connection");
		} catch (IOException e) {
			e.printStackTrace();
			log.error("RabbitMQ Failed to create connection");
			result.setOk(false);
			return result;
		}
		
		//Create a queue
		try {
			channel.queueDeclare(queue, false, false, false, null);
			log.info("RabbitMQ create queue : " + queue );
		} catch (IOException e) {
			e.printStackTrace();
			log.error("RabbitMQ Failed to queue : " + queue);
			result.setOk(false);
			return result;
		}
		
		//Publish the message to the queue
		try {
			channel.basicPublish("", queue, null, message.getBytes());
			log.info("RabbitMQ publish message : " + message );
			//Here it would be nice to grab the id of the message
			//Store it in a module with the assetid, original message, timestamps, and status
			//Technically this job is now out of the hands of entermedia
			//But we can't consider the job complete.
			//Once the job has been acknowledged by the worker, it can be set to complete.
		} catch (IOException e) {
			e.printStackTrace();
			log.error("RabbitMQ Failed to publish message : " + message);
			result.setOk(false);
			return result;
		}
		
		//The conversion message was sent fine.
		result.setOk(true);
		
		//The job is not complete yet.. Hopefully this works!
		//result.setComplete(false);
		//Lets try this instead, and add an endpoint to the api which can complete
		result.setProperty("status", "submitted");
		return result;
	}
}