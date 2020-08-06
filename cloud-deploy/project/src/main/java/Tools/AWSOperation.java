package Tools;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import org.apache.commons.cli.CommandLine;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Description: Operations for aws.
 *
 * @author linlong
 * @date 2019/4/15
 */

public class AWSOperation {
    // Source: https://amazonaws-china.com/cn/ec2/pricing/on-demand/
    private static final Map EC2_INSTANCE_TYPES = new HashMap() {{
        put("c4.large",    "hvm");
        put("c4.xlarge",   "hvm");
        put("c4.2xlarge",  "hvm");
        put("c4.4xlarge",  "hvm");
        put("c4.8xlarge",  "hvm");
        put("c5.large",    "hvm");
        put("c5.xlarge",   "hvm");
        put("c5.2xlarge",  "hvm");
        put("c5.4xlarge",  "hvm");
        put("c5.8xlarge",  "hvm");
        put("d2.xlarge",   "hvm");
        put("d2.2xlarge",  "hvm");
        put("d2.4xlarge",  "hvm");
        put("d2.8xlarge",  "hvm");
        put("i3.large",    "hvm");
        put("i3.xlarge",   "hvm");
        put("i3.2xlarge",  "hvm");
        put("i3.4xlarge",  "hvm");
        put("i3.8xlarge",  "hvm");
        put("i3.16xlarge", "hvm");
        put("m4.large",    "hvm");
        put("m4.xlarge",   "hvm");
        put("m4.2xlarge",  "hvm");
        put("m4.4xlarge",  "hvm");
        put("m4.10xlarge", "hvm");
        put("m4.16xlarge", "hvm");
        put("r3.large",    "hvm");
        put("r3.xlarge",   "hvm");
        put("r3.2xlarge",  "hvm");
        put("r3.4xlarge",  "hvm");
        put("r3.8xlarge",  "hvm");
        put("r4.large",    "hvm");
        put("r4.xlarge",   "hvm");
        put("r4.2xlarge",  "hvm");
        put("r4.4xlarge",  "hvm");
        put("r4.8xlarge",  "hvm");
        put("r4.16xlarge", "hvm");
        put("t2.micro",    "hvm");
        put("t2.small",    "hvm");
        put("t2.medium",   "hvm");
        put("t2.large",    "hvm");
        put("t2.xlarge",   "hvm");
        put("t2.2xlarge",  "hvm");
    }};

    /**
     * Check if the arguments is right.
     * @param line command line
     * @return true if the arguments is right, false otherwise.
     */
    public static boolean checkArgs(CommandLine line) {

        return line.hasOption("cluster-name") && line.hasOption("vpc-id") && line.hasOption("region");
    }

    /**
     * Get one Amazon EC2 client
     * @param line command line
     * @return amazon ec2 client instance
     */
    public static AmazonEC2 getEC2Client(CommandLine line) {
        // As we use the us-east-2 region in experiment,
        // to check if a particular AWS service is available in this region.
        if (!Region.getRegion(Regions.US_EAST_2).isServiceSupported(AmazonEC2.ENDPOINT_PREFIX)) {
            throw new RuntimeException("Warning: ec2 service is not available in us-west-2");
        }

        BasicAWSCredentials awsCreds = new BasicAWSCredentials(line.getOptionValue("accessKey"), line.getOptionValue("secretKey"));
        // Create Amazon EC2.
        AmazonEC2 ec2 = AmazonEC2ClientBuilder
            .standard()
            .withRegion(line.getOptionValue("region"))
            .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
            .build();
        return ec2;
    }

    /**
     * Get the EC2 instances in an existing cluster if available.
     * @param ec2 Amazon EC2 client
     * @param line input command line
     * @return existing instance in cluster
     */
    public static Boolean checkExistingCluster(AmazonEC2 ec2, CommandLine line) {
        String cluster = line.getOptionValue("cluster-name");
        String vpc = line.getOptionValue("vpc-id");
        System.out.println("Searching for existing cluster " + cluster + " in " + vpc + " VPC");
        Filter[] filters = new Filter[]{
            new Filter("vpc-id").withValues(vpc),
            new Filter("tag:ClusterName").withValues(cluster),
            new Filter("instance-state-code").withValues("0", "16", "64", "80"),
        };
        DescribeInstancesResult requestResult = getInstances(ec2, filters);
        return requestResult.getReservations().size() != 0;
    }

    public static Boolean checkRunningCluster(AmazonEC2 ec2, CommandLine line) {
        String cluster = line.getOptionValue("cluster-name");
        String vpc = line.getOptionValue("vpc-id");
        int clusterSize = Integer.parseInt(line.getOptionValue("slaves"));
        System.out.println("Searching for running cluster " + cluster + " in " + vpc + " VPC");
        Filter[] filters = new Filter[]{
            new Filter("vpc-id").withValues(vpc),
            new Filter("tag:ClusterName").withValues(cluster),
            new Filter("instance-state-code").withValues("16"),
        };
        DescribeInstancesResult requestResult = getInstances(ec2, filters);

        int runningInstanceNumber = 0;
        if (requestResult.getReservations().size() > 0) {
            runningInstanceNumber = requestResult.getReservations().get(0).getInstances().size();
        }

        System.out.println("Cluster size: " + clusterSize);
        System.out.println("Running instance number: " + runningInstanceNumber);

        return runningInstanceNumber == clusterSize;
    }

    /**
     * Get instances using  filters
     * @param ec2 AmazonEC2 client
     * @param filters given criteria
     * @return descriptions of instances
     */
    public static DescribeInstancesResult getInstances(AmazonEC2 ec2, Filter[] filters) {
        DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest()
            .withFilters(filters);
        return ec2.describeInstances(describeInstancesRequest);
    }

    /**
     * Launch cluster according parameters.
     * @param ec2
     * @param line
     * @return Result of launching.
     */
    public static List<Instance> launchCluster(AmazonEC2 ec2, CommandLine line) {
        // Instance type validation.
        if (!EC2_INSTANCE_TYPES.containsKey(line.getOptionValue("instance-type")) ) {
            throw new RuntimeException("Warning: Unrecognized EC2 instance type (" + line.getOptionValue("instance-type") +
                ") for instance-type");
        }
        // Launching cluster.
        Reservation clusterResponse = createInstance(ec2, line).getReservation();
        //System.out.println(clusterResponse);

        // Get list of instance in cluster.
        List<Instance> clusterInstances =  clusterResponse.getInstances();

        // Add tags to instances in cluster.
        addTags(ec2, clusterInstances, line.getOptionValue("cluster-name"));

        // Show the instances in cluster.
//        for (Instance instance : clusterInstances) {
//            System.out.println(instance.getTags() + ": " + instance.getInstanceId() + ": " + instance.getPrivateIpAddress());
//        }
        return clusterInstances;
    }

    /**
     * Create instance according run instance request.
     * @param ec2
     * @param line
     * @return result of creating.
     */
    public static RunInstancesResult createInstance(AmazonEC2 ec2, CommandLine line) {
        // Create instance in cluster.
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
            .withInstanceType(line.getOptionValue("instance-type"))
            .withImageId(line.getOptionValue("ami"))
            .withSecurityGroupIds(line.getOptionValue("security-group"))
            .withSubnetId(line.getOptionValue("subnet-id"))
            .withPlacement(new Placement(line.getOptionValue("zone")))
            .withKeyName(line.getOptionValue("key-pair"))
            .withMinCount(1)
            .withMaxCount(Integer.parseInt(line.getOptionValue("slaves")));
        return ec2.runInstances(runInstancesRequest);
    }

    /**
     * Add tags to instances in cluster.
     * @param ec2
     * @param instances in cluster.
     * @param cluster name of cluster.
     */
    public static void addTags(AmazonEC2 ec2, List<Instance> instances, String cluster) {
        for (Iterator<Instance> iterator = instances.iterator(); iterator.hasNext(); ) {
            Instance next =  iterator.next();
            Tag clusterTag = new Tag()
                .withKey("ClusterName")
                .withValue(cluster);

            Tag nameTag = new Tag()
                .withKey("Name")
                .withValue(cluster + "-" + next.getInstanceType() + "-" + next.getPrivateIpAddress());
            next.setTags(Arrays.asList(clusterTag, nameTag));

            CreateTagsRequest tagsRequest = new CreateTagsRequest()
                .withResources(next.getInstanceId())
                .withTags(clusterTag, nameTag);

            CreateTagsResult tagsResult = ec2.createTags(tagsRequest);
        }
    }

    /**
     * Write instance private ips in slaves file.
     * @param instances in cluster.
     * @throws IOException
     */
    public static void writeSlavesConfiguration(List<Instance> instances, String ipType, String file) throws IOException {
        File slavesFile = new File("./src/main/resources/" + file);
        if (!slavesFile.exists()) {
            slavesFile.createNewFile();
        } else {
            slavesFile.delete();
            slavesFile.createNewFile();
        }
        FileWriter fileWriter = new FileWriter(slavesFile);
        BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
        if (file.equals("hosts")) {
            bufferedWriter.append(
                "127.0.0.1   localhost localhost.localdomain localhost4 localhost4.localdomain4\n" +
                    "::1         localhost6 localhost6.localdomain6\n" +
                    instances.get(0).getPrivateIpAddress() + " master\n");
        } else {
            for (Iterator<Instance> iterator = instances.iterator(); iterator.hasNext(); ) {
                Instance next = iterator.next();
                if (ipType.equals("public")) {
                    bufferedWriter.append(next.getPublicIpAddress().toString() + "\n");
                } else if (ipType.equals("private")) {
                    bufferedWriter.append(next.getPrivateIpAddress().toString() + "\n");
                }
            }
        }
        bufferedWriter.close();
        fileWriter.close();
    }

    /**
     * Associate an instance with an elastic ip.
     * @param ec2
     * @param instance master
     * @return
     */
    public static AssociateAddressResult allocateMasterElasticIP(AmazonEC2 ec2, Instance instance) {
        AllocateAddressRequest allocateAddressRequest = new AllocateAddressRequest()
            .withDomain(DomainType.Vpc);
        AllocateAddressResult allocateAddressResult = ec2.allocateAddress(allocateAddressRequest);
        String allocationId = allocateAddressResult.getAllocationId();
        AssociateAddressRequest associateAddressRequest = new AssociateAddressRequest()
            .withInstanceId(instance.getInstanceId())
            .withAllocationId(allocationId);
        return ec2.associateAddress(associateAddressRequest);
    }
}
