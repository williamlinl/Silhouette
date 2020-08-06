package Tools;

import com.amazonaws.util.StringUtils;
import org.apache.commons.cli.*;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * Description: Read properties from *.properties files.
 *
 * @author linlong
 * @date 2019/4/15
 */

public class PropertyReader {

    public static Properties loadProperties(String filePath) throws IOException {
        if (StringUtils.isNullOrEmpty(filePath)) {
            throw new IOException("No such file!");
        }

        FileInputStream sourceFile = new FileInputStream(filePath);
        BufferedInputStream sourceBuffer = new BufferedInputStream(sourceFile);
        Properties sourceProperties = new Properties();

        sourceProperties.load(sourceBuffer);

        sourceBuffer.close();

        return sourceProperties;
    }

    /**
     * Configure and parse out command-line arguments.
     * @param args input parameter array
     * @return CommandLine of input parameter
     * @throws ParseException
     */
    public static CommandLine parseArgs(String[] args) throws ParseException {
        // Create a new CommandLineParser
        CommandLineParser parser = new DefaultParser();
        // Create Option object
        Options options = new Options();

        // Add option to options
        options.addOption("n", "cluster-name", true,
            "Name of cluster to deploy");
        options.addOption("s", "slaves", true,
            "Number of slaves to launch");
        options.addOption("k", "key-pair", true,
            "Key pair to use on instance");
        options.addOption("t", "instance-type", true,
            "Type of instance to launch");
        options.addOption("r", "region", true,
            "EC2 region used to launch instance in");
        options.addOption("z", "zone", true,
            "Availability zone to launch in");
        options.addOption("a", "ami", true,
            "Amazon Machine Image ID to use");
        options.addOption("placement", "placement-group", true,
            "Which placement group to try and launch instance into");
        options.addOption("security", "security-group", true,
            "Which security group to place the machines in");
        options.addOption("sub", "subnet-id", true,
            "VPC subnet to launch instance in");
        options.addOption("vpc", "vpc-id", true,
            "VPC to launch instance in");
        options.addOption("profile", "instance-profile-name", true,
            "IAM profile name to launch instances under");
        options.addOption("acc", "accessKey",true,
            "Access Key");
        options.addOption("sec", "secretKey", true,
            "Secret Key");

        // Parse the command line arguments
        CommandLine line = parser.parse(options, args);

        return line;
    }

    public static CommandLine parseArgs(String filePath) throws ParseException, IOException {
        Properties sourceProperties = loadProperties(filePath);
        List<String> propertyList = new LinkedList<String>();
        Iterator<String> propertyIterator = sourceProperties.stringPropertyNames().iterator();

        while (propertyIterator.hasNext()) {
            String tempKey = propertyIterator.next();
            String tempValue = sourceProperties.getProperty(tempKey);
            propertyList.add(tempKey + "=" + tempValue);
        }
        String [] args = (String[])propertyList.toArray(new String[propertyList.size()]);
        return parseArgs(args);
    }

}
