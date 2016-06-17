package assets;

import org.elasticsearch.search.aggregations.AggregationBuilder
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms
import org.elasticsearch.search.aggregations.metrics.sum.SumBuilder
import org.entermediadb.asset.MediaArchive
import org.openedit.WebPageRequest
import org.openedit.data.Searcher
import org.openedit.hittracker.HitTracker
import org.openedit.hittracker.SearchQuery

public void init(){
	log.info("Running aggregation search");
	WebPageRequest req = context;
	MediaArchive archive = req.getPageValue("mediaarchive");
	Searcher searcher = archive.getAssetSearcher();
	SearchQuery query = searcher.createSearchQuery();
	AggregationBuilder b = AggregationBuilders.terms("assettype_filesize").field("assettype");
	SumBuilder sum = new SumBuilder("assettype_sum");
	sum.field("filesize");
	b.subAggregation(sum);
	query.setAggregation(b);
	query.addMatches("id", "*");
	
	HitTracker hits =searcher.search(query);
	hits.getFilterOptions();
	StringTerms agginfo = hits.getAggregations().get("assettype_filesize");
	context.putPageValue("breakdownhits", hits)
	log.info(agginfo.getBuckets().size())
	log.info("hits" + hits.size());
	log.info(hits.toString());
}

init();
	