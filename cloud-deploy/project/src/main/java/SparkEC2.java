import Tools.AWSOperation;
import Tools.PropertyReader;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.sun.deploy.util.ArrayUtil;
import org.apache.commons.cli.*;

import java.io.*;
import java.util.*;

/**
 * Deploy spark cluster in ec2.
 *
 * @author lin
 * @create 2017-11-07 15:49
 */

public class SparkEC2 {

    public static void main(String[] args) throws Exception {

        AmazonEC2 ec2;
        CommandLine clusterConfig = PropertyReader.parseArgs("./src/main/resources/aws-cluster.properties");

        System.out.println("Start checking arguments...");
        // Check the necessary arguments.
        if (!AWSOperation.checkArgs(clusterConfig)) {
            throw new RuntimeException("Warning: args<>");
        } else {
             ec2 = AWSOperation.getEC2Client(clusterConfig);
        }

        // Check if the cluster existing.
        if (AWSOperation.checkExistingCluster(ec2, clusterConfig)) {
            throw new RuntimeException("Warning: the same name cluster existed");
        }

        System.out.println("Arguments done! Start launching cluster...");
        List<Instance> clusterInstances = AWSOperation.launchCluster(ec2, clusterConfig);

        while (!AWSOperation.checkRunningCluster(ec2, clusterConfig)) {
            Thread.sleep(10000);
        }

        System.out.println("Cluster done! Start ip configuration...Associate master instance with elastic ip...");
        Instance master = clusterInstances.get(0);
        AWSOperation.allocateMasterElasticIP(ec2, master);

        System.out.println("Collecting ips of instances in cluster...");
        AWSOperation.writeSlavesConfiguration(clusterInstances, "private", "slaves");
        List<Instance> masterWithIp = AWSOperation.getInstances(ec2, new Filter[]{new Filter("instance-id").withValues(master.getInstanceId())}).getReservations().get(0).getInstances();
        AWSOperation.writeSlavesConfiguration(masterWithIp, "public", "master");
        AWSOperation.writeSlavesConfiguration(masterWithIp, "private", "hosts");

        System.out.println("Job Finished!");

    }
}
