package vizone

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.HttpClient
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.entermediadb.asset.Asset
import org.entermediadb.asset.MediaArchive
import org.entermediadb.asset.scanner.MetaDataReader
import org.entermediadb.asset.search.AssetSearcher
import org.entermediadb.email.ElasticPostMail;
import org.entermediadb.modules.update.Downloader
import org.openedit.WebPageRequest
import org.openedit.page.Page

public class VizOne{
	
	private static final Log log = LogFactory.getLog(ElasticPostMail.class);
	
	def authString = "EMDEV:3nterMed1a".getBytes().encodeBase64().toString();
	protected ThreadLocal perThreadCache = new ThreadLocal();
	public void testLoadAsset(WebPageRequest inReq){
		//def addr       = "http://vizmtlvamf.media.in.cbcsrc.ca/thirdparty/asset/item?id=50"
		def addr       = "http://vizmtlvamf.media.in.cbcsrc.ca/thirdparty/asset/item?start=2&num=10000"
		def conn = addr.toURL().openConnection()
		conn.setRequestProperty( "Authorization", "Basic ${authString}" )
		conn.setRequestProperty("Accept", "application/atom+xml;type=feed");

		String content = conn.content.text;
		println content;
		MediaArchive archive = inReq.getPageValue("mediaarchive");
		AssetSearcher assetsearcher = archive.getAssetSearcher();
		ArrayList assets = new ArrayList();
		if( conn.responseCode == 200 ) {
			def rss = new XmlSlurper().parseText(content  )

			//<core:ardomeIdentity name="id">2101611020017917621</core:ardomeIdentity>
			println rss.title

			rss.entry.each {
				try{
					String vizid =it.ardomeIdentity;

					Asset asset = assetsearcher.searchByField("vizid", vizid);
					if(asset == null){
						asset = assetsearcher.createNewData();

						asset.setValue("vizid", vizid);
						String itContent = it.content.@src;
						asset.setValue("fetchurl", itContent);
						String itTitle = it.title.text();
						asset.setValue("assettitle", itTitle);
						asset.setName(it.derivativefilename.text());
						asset.setValue("importstatus", "needsdownload");
						asset.setSourcePath("vizone/${vizid}");
						assets.add(asset);

						Downloader dl = new Downloader();
						String path = "/WEB-INF/data/"	+ archive.getCatalogId() + "/originals/" + asset.getSourcePath()			+ "/" + asset.getName();

						Page finalfile = archive.getPageManager().getPage(path);
						File image = new File(finalfile.getContentItem().getAbsolutePath());
						dl.download(itContent, image);
						asset.setPrimaryFile(asset.getName());
						MetaDataReader reader = archive.getModuleManager().getBean("metaDataReader");
						reader.populateAsset(archive, finalfile.getContentItem(), asset);

						asset.setValue("importstatus","imported");
						def tasksearcher = archive.getSearcher("conversiontask");

						archive.saveAsset(asset,null);
						archive.fireMediaEvent( "importing/assetsimported", null, asset); //this will save the asset as imported
					}

				} catch(Exception e){

					log.info("Skipped " +it.ardomeIdentity + ": " + e.getMessage());



				}
			}


		}

	}








	public HttpClient getClient(String inCatalogId)
	{
		RequestConfig globalConfig = RequestConfig.custom()
				.setCookieSpec(CookieSpecs.DEFAULT)
				.build();
		CloseableHttpClient httpClient = HttpClients.custom()
				.setDefaultRequestConfig(globalConfig)
				.build();

		return httpClient;



	}


}


VizOne vz = new VizOne();
//vz.testModels();

//vz.testMetadata();

//vz.testCreateRecord();

vz.testLoadAsset(context);
//vz.testUpload(context);

vz.testSearch();

