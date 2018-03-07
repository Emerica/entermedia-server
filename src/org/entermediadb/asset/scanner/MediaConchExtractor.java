package org.entermediadb.asset.scanner;
/*
 * Shane Andrusiak - shane.andrusiak@wildtv.ca - pxnemerica@gmail.com
 * MediaConch Verification for Entermedia
 * Feb 22 2018
 * 
 */

//Include some stuff we'll need
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.entermediadb.asset.Asset;
import org.entermediadb.asset.MediaArchive;
import org.openedit.Data;
import org.openedit.repository.ContentItem;
import org.openedit.util.Exec;
import org.openedit.util.ExecResult;
import java.util.regex.*;

//Extend the MetadataExtractor Class
public class MediaConchExtractor extends MetadataExtractor
{
	//Start a log factory for mediaconch
	private static final Log log = LogFactory.getLog(MediaConchExtractor.class);
	
	//Handle for execution?
	protected Exec fieldExec;
	
	//Here the hard work is done
	public boolean extractData(MediaArchive inArchive, ContentItem inFile, Asset inAsset)
	{
		/*
		 * Todo... Libraries or Collections ?
		 * Something needs to hold the data for a mediaconch profile
		 * Have this code look up the mediaconch profile for a library, test it agaisnt the assets in the library as they are uploaded.
		 * Creates a problem where if files are not uploaded to the right library from the start... they wont get checked properly.
		 */
		
		Data LibraryId = null;
		
		//A little tag we can watch for in the logs
		log.info("MEDIACONCH -------------------- ");
		
		
		//Collection libraries = inAsset.getLibraries();
		//System.err.print("MEDIACONCH -------LIB------------- " + inAsset.getLibraries().toString() + "\n");
		//System.err.print("MEDIACONCH -------LIB------------- " + inAsset.getCatalogId().toString() + "\n");
		//System.err.print("MEDIACONCH -------LIB------------- " + inAsset.getCollections().toString() + "\n");
		
		
		
		//System.err.println("MediaConch Library Id is: " +  LibraryId.toString());
		
		
		//Get the media type
		String mediatype = inArchive.getMediaRenderType(inAsset.getFileFormat());
		
		//If the file is video or audio, we can check this file
		if( "video".equals(mediatype ) || "audio".equals(mediatype)) 
		{
			
			//Build a list of arguments
			List<String> args = new ArrayList<String>();
			
			//Add the full text option
			args.add("-ft"); 
			
			//Request the use of a profile.xml
			args.add("-p");
			
			//TODO: Locate the filename for a profile that's been uploaded to EM.
			//Use that here so each library can have a custom profile.

			
			args.add("/home/shane/Downloads/CanadaHD.xml");
			
			//Add the filename
			args.add(inFile.getAbsolutePath());

			//Execute mediaconch with the above arguments.
			ExecResult resulttext = getExec().runExec("mediaconch", args, true);
			
			//If there was an error getting the result
			if( !resulttext.isRunOk())
			{
				//Get the output from the error
				String error = resulttext.getStandardError();
				
				//Mark the asset mediaconch info as an error status
				inAsset.setProperty("mediaconch_status", "Error.");
				
				//Print the error information into the report section
				inAsset.setProperty("mediaconch_report", error.toString());
				
				//Log the mediaconch error
				log.error("error " + error);
				
				//Bail
				return false;
			}
			
			//Try to get output from stdout
			String textinfo = resulttext.getStandardOut();
			
			//If we got no stdout
			if( textinfo== null)
			{
				//Get from stderr
				textinfo = resulttext.getStandardError();
			}
			
			//If the textinfo still hasn't come back. Probably shouldn't happen, but catch it.
			if( textinfo == null)
			{
				//Update the status with an Error
				inAsset.setProperty("mediaconch_status", "Error.");
				
				//Update the report, saying nothing came back.
				inAsset.setProperty("mediaconch_report", "No data returned");
				
				//Bail.
				return false;
			}
			
			//Use regex to search for "Outcome: pass", if this is found, we are in good shape
			Pattern p = Pattern.compile("Outcome: pass"); 
			
			//Get the matches
			Matcher m = p.matcher(textinfo.toString()); 
			
			//If we have a match , woohoo
			if (m.matches()) {   

				//Set the status to passed.
				inAsset.setProperty("mediaconch_status", "Passed.");
				
				//Log the event
				log.error("MediaConch passed the asset.");
			}else {
				
				//Set the status to failed.
				inAsset.setProperty("mediaconch_status", "Failed.");
				
				//Log the event
			    log.error("MediaConch failed the asset.");
			}
			
			
			//Update the report results with the text output from mediaconch.
			//Css below added to : /emshare/theme/styles/custom.css
			
			/*#mediaconch_report { 
				font-size: 10px;
				font-family: monospace;
				display: block;
				white-space: pre;
				margin: 1em 0;
				}
			*/
			inAsset.setProperty("mediaconch_report", ""
					+ "<pre class='mediaconch_report'>"
					+ textinfo.toString() 
					+ "</pre>");
			
			//Debug, dump the mediaconch log to console.
			log.info("MEDIACONCH -------------------- " + textinfo.toString());
			//Bail
			return true;
		}
		//Bail
		return false;
	}
	
	//Support Exec
	public Exec getExec()
	{
		return fieldExec;
	}
	
	//Support Exec
	public void setExec(Exec exec)
	{
		fieldExec = exec;
	}

}