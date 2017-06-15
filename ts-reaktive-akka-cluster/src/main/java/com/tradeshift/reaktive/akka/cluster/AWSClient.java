package com.tradeshift.reaktive.akka.cluster;

import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingInstancesRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingInstancesResult;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStateName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AWSClient {

    private final AmazonEC2Client ec2Client;
    private final AmazonAutoScalingClient autoScalingClient;

    public AWSClient(Region region) {
        this(region, new InstanceProfileCredentialsProvider());
    }

    public AWSClient(Region region, InstanceProfileCredentialsProvider credentials) {
        this(region, new AmazonEC2Client(credentials), new AmazonAutoScalingClient(credentials));
    }

    public AWSClient(Region region, AmazonEC2Client ec2Client, AmazonAutoScalingClient autoScalingClient) {
        this.autoScalingClient = autoScalingClient;
        this.autoScalingClient.setRegion(region);

        this.ec2Client = ec2Client;
        this.ec2Client.setRegion(region);
    }

    /**
     * Returns a list of private IPs of running instances, which are in the same auto-scaling group
     * as current running instance. All IPs in a list are sorted by instance launch date. A list
     * can contain the private IP of current running instance if it is the first launched instance,
     * otherwise it is excluded. The list is a snapshot and can be freely modified.
     */
    public List<String> getSiblingIps(String currentInstanceId) {
        String group = getAutoScalingGroupName(currentInstanceId);
        List<String> instanceIds = getAutoScalingGroupInstanceIds(group);

        List<Instance> siblingInstances = new ArrayList<>();
        for (String instanceId : instanceIds) {
            Instance instance = getInstance(instanceId);
            if (instance.getState().getName().equals(InstanceStateName.Running.toString())) {
                siblingInstances.add(instance);
            }
        }

        siblingInstances.sort(Comparator.comparing(Instance::getLaunchTime));

        if (!currentInstanceId.equals(siblingInstances.get(0).getInstanceId())) {
            siblingInstances.removeIf(i -> i.getInstanceId().equals(currentInstanceId));
        }

        return siblingInstances.stream().map(Instance::getPrivateIpAddress).collect(Collectors.toList());
    }

    public Instance getInstance(String instanceId) {
        DescribeInstancesResult result = ec2Client.describeInstances(new DescribeInstancesRequest().withInstanceIds(instanceId));
        return result.getReservations().get(0).getInstances().get(0);
    }

    public String getAutoScalingGroupName(String instanceId) {
        DescribeAutoScalingInstancesRequest request = new DescribeAutoScalingInstancesRequest().withInstanceIds(instanceId);
        DescribeAutoScalingInstancesResult result = autoScalingClient.describeAutoScalingInstances(request);

        if (result.getAutoScalingInstances().size() > 1) {
            throw new IllegalStateException("Instance belongs to more than one auto-scaling groups");
        }

        return result.getAutoScalingInstances().get(0).getAutoScalingGroupName();
    }

    public List<String> getAutoScalingGroupInstanceIds(String groupName) {
        DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(groupName);
        DescribeAutoScalingGroupsResult result = autoScalingClient.describeAutoScalingGroups(request);

        List<String> groupInstances = new ArrayList<>();

        for (com.amazonaws.services.autoscaling.model.Instance instance : result.getAutoScalingGroups().get(0).getInstances()) {
            groupInstances.add(instance.getInstanceId());
        }

        return groupInstances;
    }
}
