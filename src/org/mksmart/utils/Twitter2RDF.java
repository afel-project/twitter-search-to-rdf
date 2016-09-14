package org.mksmart.utils;

import twitter4j.*;
import twitter4j.conf.*;
import java.util.List;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;
import java.util.Calendar;

public class Twitter2RDF {
    
    private Twitter twitter;
    private static final String NS   = "http://data.afel-project.eu/twitter/";
    private static final String SIOC = "http://rdfs.org/sioc/ns#";

    public Twitter2RDF(String consumerKey, String consumerSecret, 
		       String accessToken, String accessSecret){
	ConfigurationBuilder cb = new ConfigurationBuilder();
	cb.setDebugEnabled(true)
	    .setOAuthConsumerKey(consumerKey)
	    .setOAuthConsumerSecret(consumerSecret)
	    .setOAuthAccessToken(accessToken)
	    .setOAuthAccessTokenSecret(accessSecret);
	TwitterFactory tf = new TwitterFactory(cb.build());
	twitter = tf.getInstance();   
	//	Twitter twitter = TwitterFactory.getSingleton();
    }


    public Model extract(String q, int limit) throws TwitterException {
	Model m = ModelFactory.createDefaultModel();
	Query query = new Query(q);
	boolean cont = true;
	int count = 0;
	while(cont) {
	    query.count(100);
	    QueryResult result = twitter.search(query);
	    for (Status status : result.getTweets()) {
		String tweetURI = NS+"tweet/"+status.getId();
		System.err.println(tweetURI);
		Resource r = m.createResource(tweetURI);
		r.addProperty(RDF.type, m.createResource(SIOC+"Post"));	
		Calendar cal = Calendar.getInstance();
		cal.setTime(status.getCreatedAt());
		r.addProperty(m.createProperty("http://purl.org/dc/terms/published"), m.createTypedLiteral(cal));
		r.addProperty(m.createProperty("http://vocab.afel-project.eu/social/favouriteCount"), m.createTypedLiteral(status.getFavoriteCount()));
		if (status.getGeoLocation()!=null){
		    r.addProperty(m.createProperty("http://www.w3.org/2003/01/geo/wgs84_pos#lat"), m.createTypedLiteral(status.getGeoLocation().getLatitude()));
		    r.addProperty(m.createProperty("http://www.w3.org/2003/01/geo/wgs84_pos#long"), m.createTypedLiteral(status.getGeoLocation().getLongitude()));
		}
		long repto = status.getInReplyToStatusId();
		if (repto >  0){
		    m.createResource(NS+"tweet/"+repto).addProperty(m.createProperty(SIOC+"has_reply"), r);
		}
		String lang = status.getLang();
		if (lang!=null){
		    r.addProperty(m.createProperty("http://purl.org/dc/terms/language"), m.createTypedLiteral(lang));
		}
		// This needs more as Places are complex objects
		Place pl = status.getPlace();
		if (pl!=null){
		    Resource plr = m.createResource(NS+"place/"+urify(pl.getFullName()));
		    r.addProperty(m.createProperty("http://www.w3.org/2003/01/geo/wgs84_pos#location"), plr);
		}
		r.addProperty(m.createProperty("http://vocab.afel-project.eu/social/sharingCount"), m.createTypedLiteral(status.getRetweetCount()));
		// getScopes() ??
		// getSource() ??
		r.addProperty(m.createProperty("http://vocab.afel-project.eu/social/content"), m.createTypedLiteral(status.getText()));
		User user = status.getUser();
		if (user!=null){
		    String userURI = NS+"user/"+user.getId();
		    Resource rUser = m.createResource(userURI);
		    r.addProperty(m.createProperty(SIOC+"has_creator"), rUser);
		    rUser.addProperty(m.createProperty("http://purl.org/dc/terms/identifier"), m.createTypedLiteral(user.getScreenName()));
		    rUser.addProperty(m.createProperty("http://xmlns.com/foaf/0.1/name"), m.createTypedLiteral(user.getName()));
		    rUser.addProperty(m.createProperty("http://purl.org/dc/terms/description"), m.createTypedLiteral(user.getDescription()));
		    rUser.addProperty(m.createProperty("http://xmlns.com/foaf/0.1/depiction"), m.createTypedLiteral(user.getOriginalProfileImageURL()));
		}
		// isPossiblySensitive() ??
		//		System.out.println(status.getCreatedAt()+" -- "+status.getId()+" @" + status.getUser().getScreenName() + ":" + status.getText());
	    }
	    cont = result.hasNext();
	    if (cont) 
		query = result.nextQuery();
	    // System.out.println("===== "+(count++)+" ======");
	    if ((count*100)>=limit) break;
	}
	return m;
    }

    private static String urify(String s){
	return s.toLowerCase().replaceAll(" ","_").replaceAll(",", "");
    }

    public static void main(String[] args) {
	try{
	    Twitter2RDF t2r = new Twitter2RDF(args[0],args[1],args[2], args[3]);
	    Model m = t2r.extract(args[4], Integer.parseInt(args[5]));
	    RDFDataMgr.write(System.out, m, RDFFormat.NTRIPLES);
	} catch(TwitterException e){
	    e.printStackTrace();
	}
    }

}
