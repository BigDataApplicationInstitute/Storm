package ReadFile.Stage;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.task.ShellBolt;
import backtype.storm.topology.IRichBolt;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import backtype.storm.utils.Utils;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.OutputCollector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;

import ReadFile.Stage.spout.RandomSentenceSpout;

/**
 * This topology demonstrates how to count distinct words from
 * a stream of words.
 *
 * 
 *
 */
public class SentenceWordCountTopology {

  /**
   * Constructor - does nothing
   */

   //Note: Constructor must match class name
  private SentenceWordCountTopology() { }



  /**
   * A bolt that counts the words that it receives
   */
  static class CountBolt extends BaseRichBolt {

    // To output tuples from this bolt to the next stage bolts, if any
    private OutputCollector collector;

    // Map to store the count of the words
    private Map<String, Integer> countMap;

    @Override
    public void prepare(
        Map                     map,
        TopologyContext         topologyContext,
        OutputCollector         outputCollector)
    {

      // save the collector for emitting tuples
      collector = outputCollector;

      // create and initialize the map
      countMap = new HashMap<String, Integer>();
    }

    @Override
    public void execute(Tuple tuple)
    {
      //**************************************************
      //BEGIN YOUR CODE - Part 1a
      //Check if incoming word is in countMap.  If word does not
      //exist then add word with count = 1, if word exist then
      //increment count.

      //Syntax to get the word from the 1st column of incoming tuple
      String word = tuple.getString(0);

      // check if the word is present in the map
      if (countMap.get(word) == null) {

      // not present, add the word with a count of 1
      countMap.put(word, 1);
      } else {

      // already there, hence get the count
      Integer val = countMap.get(word);

      // increment the count and save it to the map
      countMap.put(word, ++val);
    }

      //After countMap is updated, emit word and count to output collector
      // Syntax to emit the word and count (uncomment to emit)
      collector.emit(new Values(word, countMap.get(word)));

      //END YOUR CODE
      //***************************************************
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer)
    {
      // tell storm the schema of the output tuple for this spout
      // tuple consists of a two columns called 'word' and 'count'

      // declare the first column 'word', second colmun 'count'

      //****************************************************
      //BEGIN YOUR CODE - part 1b
      //uncomment line below to declare output

      outputFieldsDeclarer.declare(new Fields("word","count"));

      //END YOUR CODE
      //****************************************************
    }
  }

  /**
   * A bolt that prints the word and count to redis
   */
  static class ReportBolt extends BaseRichBolt
  {
    // place holder to keep the connection to redis
    transient RedisConnection<String,String> redis;

    @Override
    public void prepare(
        Map                     map,
        TopologyContext         topologyContext,
        OutputCollector         outputCollector)
    {
      // instantiate a redis connection
      RedisClient client = new RedisClient("localhost",6379);

      // initiate the actual connection
      redis = client.connect();
    }

    @Override
    public void execute(Tuple tuple)
    {
      // access the first column 'word'
      String word = tuple.getStringByField("word");

      // access the second column 'count'
      Integer count = tuple.getIntegerByField("count");

      // publish the word count to redis using word as the key
      redis.publish("WordCountTopology", word + "|" + Long.toString(count));
    }

    public void declareOutputFields(OutputFieldsDeclarer declarer)
    {
      // nothing to add - since it is the final bolt
    }
  }

  public static void main(String[] args) throws Exception
  {
    // create the topology
    TopologyBuilder builder = new TopologyBuilder();

    // attach the word spout to the topology - parallelism of 5
    //builder.setSpout("word-spout", new WordSpout(), 5);

    // attach sentence spout to the topology - parallelism of 1
    builder.setSpout("sentence-spout", new RandomSentenceSpout(), 1);

    // attach split sentence split bolt to topology - parallelism of 15
    //builder.setBolt("split-sentence-bolt", new SplitSentenceBolt(), 15).fieldsGrouping("sentence-spout", new Fields("sentence"));
    //builder.setBolt("split-sentence-bolt", new SplitSentenceBolt(), 15).shuffleGrouping("sentence-spout");

    // attach the count bolt using fields grouping - parallelism of 15
    builder.setBolt("count-bolt", new CountBolt(), 15).globalGrouping("sentence-spout");

    // attach the report bolt using global grouping - parallelism of 1
    //***************************************************
    // BEGIN YOUR CODE - part 2

    builder.setBolt("report-bolt", new ReportBolt(), 1).globalGrouping("count-bolt");


    // END YOUR CODE
    //***************************************************

    // create the default config object
    Config conf = new Config();
    conf.put("inputFile", args[0]);
    // set the config in debugging mode
    conf.setDebug(true);

    if (args != null && args.length > 0) {

      // run it in a live cluster

      // set the number of workers for running all spout and bolt tasks
      conf.setNumWorkers(3);
      LocalCluster cluster = new LocalCluster();
      // create the topology and submit with config
      cluster.submitTopology("SentenceWordCountTopology", conf, builder.createTopology());

    } else {

      // run it in a simulated local cluster

      // set the number of threads to run - similar to setting number of workers in live cluster
      conf.setMaxTaskParallelism(3);

      // create the local cluster instance
      LocalCluster cluster = new LocalCluster();

      // submit the topology to the local cluster
      // name topology
      cluster.submitTopology("sentence-word-count", conf, builder.createTopology());

      //**********************************************************************
      // let the topology run for 30 seconds. note topologies never terminate!
      Thread.sleep(30000);
      //**********************************************************************

      // we are done, so shutdown the local cluster
      cluster.shutdown();
    }
  }
}
