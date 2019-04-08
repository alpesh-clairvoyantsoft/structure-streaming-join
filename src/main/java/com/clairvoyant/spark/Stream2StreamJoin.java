package com.clairvoyant.spark;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.expr;
import static org.apache.spark.sql.functions.from_json;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.streaming.StreamingQueryException;
import org.apache.spark.sql.streaming.Trigger;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Stream2StreamJoin  implements CommandLineRunner{
	
	
	
	private static final Logger LOGGER =
		      LoggerFactory.getLogger(Stream2StreamJoin.class);
	
	@Value("${kafka.bootstrap.server}")
	private String bootstrapServers;
	
	@Value("${kafka.userevent}")
	private String usereventTopic;
	
	@Value("${kafka.paymentevent}")
	private String paymenteventTopic;
	
	public void processData() {
		
		System.out.println(bootstrapServers);
		System.out.println(usereventTopic);
		System.out.println(paymenteventTopic);
		
		LOGGER.info(bootstrapServers);
		LOGGER.info(usereventTopic);
		LOGGER.info(paymenteventTopic);
		
		
//		SparkConf sparkConf = new SparkConf().setAppName("Stream2StreamJoin").setMaster("local[*]");
		
//		JavaStreamingContext jssc = new JavaStreamingContext(sparkConf, Durations.seconds(1));
		
		
  
		SparkSession spark = SparkSession
				  .builder()
				  .appName("Stream2StreamJoin")
				  .master("local[*]")
				  .getOrCreate();
		
		spark.sparkContext().setLogLevel("ERROR");
		
		StructType userSchema =  DataTypes.createStructType(new StructField[] { 
				DataTypes.createStructField("userId", DataTypes.StringType, true),
				DataTypes.createStructField("firstname", DataTypes.StringType, true),
				DataTypes.createStructField("lastname", DataTypes.StringType, true),
				DataTypes.createStructField("phonenumber", DataTypes.StringType, true),
				DataTypes.createStructField("usertimestamp", DataTypes.TimestampType, true)
				});
		
		
		StructType paymentSchema =  DataTypes.createStructType(new StructField[] { 
				DataTypes.createStructField("paymentUserId", DataTypes.StringType, true),
				DataTypes.createStructField("amount", DataTypes.StringType, true),
				DataTypes.createStructField("location", DataTypes.StringType, true),				
				DataTypes.createStructField("paymenttimestamp", DataTypes.TimestampType, true)
				});
		
//		Dataset<UserEvent> userDataSet=spark.readStream().format("kafka")
//				  .option("kafka.bootstrap.servers", bootstrapServers)
//				  .option("subscribe", usereventTopic)
//				  .load().selectExpr("CAST(value  AS STRING) as userEvent")
//				     .select(functions.from_json(functions.col("userEvent"),userSchema).as("user"))
//				     .select("user.*")
//				     .as(Encoders.bean(UserEvent.class)); 
//		
//		Dataset<PaymentEvent> paymentDataSet=spark.readStream().format("kafka")
//				  .option("kafka.bootstrap.servers", bootstrapServers)
//				  .option("subscribe", paymenteventTopic)
//				  .load().selectExpr("CAST( value AS STRING) as paymentEvent")
//				     .select(functions.from_json(functions.col("paymentEvent"),paymentSchema).as("payment"))
//				     .select("payment.*")
//				     .as(Encoders.bean(PaymentEvent.class));
		
		
		//--------Stream1 from user Topic  --------
		Dataset<Row> rawUserData=spark.readStream().format("kafka")
						  	.option("kafka.bootstrap.servers", bootstrapServers)
						  	.option("subscribe", usereventTopic)
						  	.option("startingOffsets", "earliest")
						  	.option("failOnDataLoss", "false")
						  	.load();
		
		Dataset<Row> userDataSet = rawUserData
							.selectExpr("cast (value as string) as json")
						 	.select(from_json(col("json"), userSchema).as("userData"))
						 	.select("userData.*");
				  
		Dataset<Row> rawPaymentData=spark.readStream().format("kafka")
						  	.option("kafka.bootstrap.servers", bootstrapServers)
						  	.option("subscribe", paymenteventTopic)
						  	.option("startingOffsets", "earliest")
						  	.option("failOnDataLoss", "false")
						  	.load();
		Dataset<Row> paymentDataSet = rawPaymentData
							.selectExpr("cast (value as string) as json")
						 	.select(functions.from_json(functions.col("json"), paymentSchema).as("paymentData"))
						 	.select("paymentData.*");
		
		Dataset<Row> userDataSetWithWatermark = userDataSet.withWatermark("usertimestamp", "20 seconds");
		
		Dataset<Row> paymentDataSetWithWatermark = paymentDataSet.withWatermark("paymenttimestamp", "20 seconds");
		
		Dataset<Row> joindataSet =	userDataSetWithWatermark.join(
				paymentDataSetWithWatermark,
				  expr(
						  "userId = paymentUserId")
				);
		

				
		try {
//			Saving the result stream to file
			joindataSet.writeStream()
		      .format("console")
		      .outputMode("append")
		      .option("checkpointLocation", "/tmp/clairvoyant/checkpoint")
		      .option("truncate","false")
		      .trigger(Trigger.ProcessingTime("20 seconds"))
		      .option("path", "/tmp/clairvoyant/output-streams")
		      .start();
			
			paymentDataSetWithWatermark.writeStream()
		      .format("console")
		      .outputMode("append")
//		      .option("checkpointLocation", "/tmp/clairvoyant/checkpoint")
		      .option("truncate","false")
		      .trigger(Trigger.ProcessingTime("20 seconds"))
		      .option("path", "/tmp/clairvoyant/output-streams")
		      .start();	
			
		      userDataSetWithWatermark.writeStream()
		      .format("console")
		      .outputMode("append")
//		      .option("checkpointLocation", "/tmp/clairvoyant/checkpoint")
		      .option("truncate","false")
		      .trigger(Trigger.ProcessingTime("20 seconds"))
		      .option("path", "/tmp/clairvoyant/output-streams")
		      .start();
		      
//		      .awaitTermination();
			spark.streams().awaitAnyTermination();
			spark.stop();
		} catch (StreamingQueryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    }

	@Override
	public void run(String... args) throws Exception {
		processData();
		
	}
	
	public static void main(String[] args) throws Exception {
		
		System.setProperty("hadoop.home.dir", "/Users/alpeshpatel/workspace/java/spark-kafka-streaming");
		
		SpringApplication.run(Stream2StreamJoin.class, args);
	}

}
