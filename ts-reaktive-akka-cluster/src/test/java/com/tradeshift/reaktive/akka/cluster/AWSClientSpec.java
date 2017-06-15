package com.tradeshift.reaktive.akka.cluster;

import static org.forgerock.cuppa.Cuppa.beforeEach;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.amazonaws.regions.Region;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.AutoScalingInstanceDetails;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingInstancesRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingInstancesResult;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.InstanceStateName;
import com.amazonaws.services.ec2.model.Reservation;

@RunWith(CuppaRunner.class)
public class AWSClientSpec {

    final AmazonEC2Client ec2Client = mock(AmazonEC2Client.class);
    final AmazonAutoScalingClient autoScalingClient = mock(AmazonAutoScalingClient.class);

    final Region region = mock(Region.class);
    final AWSClient client = new AWSClient(region, ec2Client, autoScalingClient);

    {
        describe("AWSClient", () -> {


            beforeEach(() -> {                
                when(autoScalingClient.describeAutoScalingInstances(eq(new DescribeAutoScalingInstancesRequest().withInstanceIds("instanceId"))))
                .thenReturn(new DescribeAutoScalingInstancesResult().withAutoScalingInstances(
                    new AutoScalingInstanceDetails().withAutoScalingGroupName("auto-scaling")));

                when(autoScalingClient.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames("auto-scaling")))
                .thenReturn( new DescribeAutoScalingGroupsResult().withAutoScalingGroups(
                    new AutoScalingGroup().withInstances(
                        new com.amazonaws.services.autoscaling.model.Instance().withInstanceId("instanceId"),
                        new com.amazonaws.services.autoscaling.model.Instance().withInstanceId("instanceId2"),
                        new com.amazonaws.services.autoscaling.model.Instance().withInstanceId("instanceId3"),
                        new com.amazonaws.services.autoscaling.model.Instance().withInstanceId("instanceId4"))));               
            });

            describe("AWSClient.getSiblingIps", () -> {
                it("should return a list of IPs of the running instances, sorted by launch date, excluding own IP if it's not the first launched instance", () -> {

                    when(ec2Client.describeInstances(eq(new DescribeInstancesRequest().withInstanceIds("instanceId"))))
                    .thenReturn(createInstance("instanceId", "ip", Date.from(Instant.now()), InstanceStateName.Running));

                    when(ec2Client.describeInstances(eq(new DescribeInstancesRequest().withInstanceIds("instanceId2"))))
                    .thenReturn(createInstance("instanceId2", "ip2", Date.from(Instant.now()), InstanceStateName.Running));

                    when(ec2Client.describeInstances(eq(new DescribeInstancesRequest().withInstanceIds("instanceId3"))))
                    .thenReturn(createInstance("instanceId3", "ip3", Date.from(Instant.now().minusSeconds(5)), InstanceStateName.Running));

                    when(ec2Client.describeInstances(eq(new DescribeInstancesRequest().withInstanceIds("instanceId4"))))
                    .thenReturn(createInstance("instanceId4", "ip4", Date.from(Instant.now()), InstanceStateName.Terminated));

                    List<String> siblingIps = client.getSiblingIps("instanceId");

                    assertThat(siblingIps).hasSize(2).containsExactly("ip3", "ip2");
                });

                it("should return a list of IPs of the running instances,  sorted by launch date, including own IP if it's the first launched instance", () -> {

                    when(ec2Client.describeInstances(eq(new DescribeInstancesRequest().withInstanceIds("instanceId"))))
                    .thenReturn(createInstance("instanceId", "ip", Date.from(Instant.now().minusSeconds(10)), InstanceStateName.Running));

                    when(ec2Client.describeInstances(eq(new DescribeInstancesRequest().withInstanceIds("instanceId2"))))
                    .thenReturn(createInstance("instanceId2", "ip2", Date.from(Instant.now()), InstanceStateName.Running));

                    when(ec2Client.describeInstances(eq(new DescribeInstancesRequest().withInstanceIds("instanceId3"))))
                    .thenReturn(createInstance("instanceId3", "ip3", Date.from(Instant.now().minusSeconds(5)), InstanceStateName.Running));

                    when(ec2Client.describeInstances(eq(new DescribeInstancesRequest().withInstanceIds("instanceId4"))))
                    .thenReturn(createInstance("instanceId4", "ip4", Date.from(Instant.now()), InstanceStateName.Terminated));

                    List<String> siblingIps = client.getSiblingIps("instanceId");

                    assertThat(siblingIps).hasSize(3).containsExactly("ip", "ip3", "ip2");
                });

            });

            describe("AWSClient.getAutoScalingGroupName", () -> {
                it("should return the name of auto-scaling group for a given instanceId", () -> {
                    DescribeAutoScalingInstancesRequest request = new DescribeAutoScalingInstancesRequest().withInstanceIds("instanceId");

                    DescribeAutoScalingInstancesResult result = new DescribeAutoScalingInstancesResult()
                        .withAutoScalingInstances(new AutoScalingInstanceDetails().withAutoScalingGroupName("auto-scaling"));

                    when(autoScalingClient.describeAutoScalingInstances(eq(request))).thenReturn(result);

                    String group = client.getAutoScalingGroupName("instanceId");
                    assertThat(group).isEqualTo("auto-scaling");
                });

                it("should throw an exception if instance for a given instanceId belongs to more than 1 auto-scaling group", () -> {
                    DescribeAutoScalingInstancesRequest request = new DescribeAutoScalingInstancesRequest().withInstanceIds("instanceId");

                    DescribeAutoScalingInstancesResult result = new DescribeAutoScalingInstancesResult()
                        .withAutoScalingInstances(
                            new AutoScalingInstanceDetails().withAutoScalingGroupName("auto-scaling1"),
                            new AutoScalingInstanceDetails().withAutoScalingGroupName("auto-scaling2"));

                    when(autoScalingClient.describeAutoScalingInstances(eq(request))).thenReturn(result);

                    assertThatThrownBy(() -> client.getAutoScalingGroupName("instanceId")).isInstanceOf(IllegalStateException.class);
                });

            });

            describe("AWSClient.getInstance", () -> {
                it("should return instance for a given instanceId", () -> {
                    DescribeInstancesRequest request = new DescribeInstancesRequest().withInstanceIds("instanceId");

                    DescribeInstancesResult result = new DescribeInstancesResult()
                        .withReservations(new Reservation()
                            .withInstances(new Instance().withInstanceId("instanceId")));

                    when(ec2Client.describeInstances(eq(request))).thenReturn(result);

                    Instance instance = client.getInstance("instanceId");
                    assertThat(instance).isNotNull();
                    assertThat(instance.getInstanceId()).isEqualTo("instanceId");
                });
            });

            describe("AWSClient.getAutoScalingGroupInstanceIds", () -> {
                it("should return a list of instanceIds for a given auto-scaling group name", () -> {
                    DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames("groupName");

                    DescribeAutoScalingGroupsResult result = new DescribeAutoScalingGroupsResult().withAutoScalingGroups(
                        new AutoScalingGroup().withInstances(
                            new com.amazonaws.services.autoscaling.model.Instance(),
                            new com.amazonaws.services.autoscaling.model.Instance()
                            ));

                    when(autoScalingClient.describeAutoScalingGroups(request)).thenReturn(result);

                    List<String> groupInstanceIds = client.getAutoScalingGroupInstanceIds("groupName");
                    assertThat(groupInstanceIds).hasSize(2);
                });
            });
        });
    }

    private DescribeInstancesResult createInstance(String instanceId, String privateIp, Date launchDate, InstanceStateName state) {
        return new DescribeInstancesResult().withReservations(
            new Reservation().withInstances(new Instance()
                .withInstanceId(instanceId)
                .withPrivateIpAddress(privateIp)
                .withLaunchTime(launchDate)
                .withState(new InstanceState().withName(state))));
    }
}
