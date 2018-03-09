package org.entermediadb.asset.convert;

import org.openedit.data.BaseData;
import org.openedit.repository.ContentItem;

public class ConvertResult extends BaseData
{
	protected boolean fieldOk;
	protected boolean fieldComplete;
	protected boolean fieldQueued;
	protected String fieldError;
	protected ContentItem fieldOutput;
	protected ConvertInstructions fieldInstructions;
	
	public ConvertInstructions getInstructions()
	{
		return fieldInstructions;
	}

	public void setInstructions(ConvertInstructions inInstructions)
	{
		fieldInstructions = inInstructions;
	}

	public ContentItem getOutput()
	{
		return fieldOutput;
	}

	public void setOutput(ContentItem inOutput)
	{
		fieldOutput = inOutput;
	}

	public boolean isComplete() 
	{
		return fieldComplete;
	}
	
	public void setComplete(boolean inComplete) 
	{
		fieldComplete = inComplete;
	}
	
	public boolean isQueued() 
	{
		return fieldQueued;
	}
	
	public void setQueued(boolean inQueued) 
	{
		fieldComplete = inQueued;
	}
	
	public String getError()
	{
		return fieldError;
	}
	
	public void setError(String inError)
	{
		fieldError = inError;
	}
	public boolean isError()
	{
		return fieldError != null;
	}
	
	public boolean isOk()
	{
		return fieldOk;
	}
	public void setOk(boolean inOk)
	{
		fieldOk = inOk;
	}
}
