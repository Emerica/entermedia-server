package org.entermediadb.asset.convert;

import org.entermediadb.asset.MediaArchive;
import org.openedit.Data;
import org.openedit.page.manage.PageManager;
import org.openedit.util.Exec;

public interface MediaTranscoder
{
	/**
	 * The creator is selected based on the output format as found here: fileformat.xml. The question becomes can this creator handle this input format?
	 * @param inArchive
	 * @param inInputType
	 * @return
	 */
	boolean canReadIn(MediaArchive inArchive, String inInputType);

	
	/**
	 * We now need to actually do the creation of this output file
	 * @param inArchive
	 * @param inAsset
	 * @param inOut
	 * @param inStructions
	 * @return
	 */
	ConvertResult convert(ConvertInstructions inStructions);

	public ConvertResult updateStatus(Data inTask, ConvertInstructions inStructions );

	void setPageManager(PageManager inPageManager);

	void setExec(Exec inExec);
	
}