package com.minicloud.api.architecture;

import com.minicloud.api.domain.*;
import com.minicloud.api.messaging.SnsTopic;
import com.minicloud.api.messaging.SqsQueue;
import com.minicloud.api.messaging.SnsService;
import com.minicloud.api.messaging.SqsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ArchitectureTopologyService {

    private final VpcRepository vpcRepository;
    private final SubnetRepository subnetRepository;
    private final InstanceRepository instanceRepository;
    private final BucketRepository bucketRepository;
    private final FunctionRepository functionRepository;
    private final SqsService sqsService;
    private final SnsService snsService;

    public Map<String, Object> getTopologyGraph() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        // 1. VPCs
        for (Vpc vpc : vpcRepository.findAll()) {
            nodes.add(Map.of("id", "vpc-" + vpc.getId(), "name", vpc.getName() != null ? vpc.getName() : "VPC", "type", "VPC", "cidr", vpc.getCidrBlock()));
        }

        // 2. Subnets
        for (Subnet sub : subnetRepository.findAll()) {
            String subId = "sub-" + sub.getId();
            nodes.add(Map.of("id", subId, "name", sub.getName() != null ? sub.getName() : "Subnet", "type", "SUBNET", "cidr", sub.getCidrBlock()));
            if (sub.getVpcId() != null) {
                edges.add(Map.of("source", "vpc-" + sub.getVpcId(), "target", subId, "relation", "contains"));
            }
        }

        // 3. Compute Instances
        for (Instance inst : instanceRepository.findAll()) {
            String instId = "inst-" + inst.getId();
            nodes.add(Map.of("id", instId, "name", inst.getName() != null ? inst.getName() : "EC2", "type", "EC2", "state", inst.getState().name()));
            if (inst.getSubnetId() != null) {
                edges.add(Map.of("source", "sub-" + inst.getSubnetId(), "target", instId, "relation", "hosted_in"));
            }
        }

        // 4. S3 Buckets
        for (Bucket b : bucketRepository.findAll()) {
            String bId = "s3-" + b.getName();
            nodes.add(Map.of("id", bId, "name", b.getName(), "type", "S3", "objects", b.getObjectCount()));
        }

        // 5. Lambda Functions
        for (Function f : functionRepository.findAll()) {
            String fId = "lambda-" + f.getName();
            nodes.add(Map.of("id", fId, "name", f.getName(), "type", "LAMBDA", "runtime", f.getRuntime()));
        }

        // 6. Messaging
        for (SqsQueue q : sqsService.listQueues()) {
            String qId = "sqs-" + q.getName();
            nodes.add(Map.of("id", qId, "name", q.getName(), "type", "SQS", "fifo", q.isFifo()));
        }
        for (SnsTopic t : snsService.listTopics()) {
            String tId = "sns-" + t.getName();
            nodes.add(Map.of("id", tId, "name", t.getName(), "type", "SNS"));
            for (var sub : t.getSubscriptions()) {
                if ("sqs".equalsIgnoreCase(sub.getProtocol())) {
                    String qName = sub.getEndpoint();
                    if (qName.contains(":")) qName = qName.substring(qName.lastIndexOf(":") + 1);
                    edges.add(Map.of("source", tId, "target", "sqs-" + qName, "relation", "publishes_to"));
                }
            }
        }

        return Map.of("nodes", nodes, "edges", edges, "totalResources", nodes.size());
    }

    public String generateMermaidDiagram() {
        StringBuilder sb = new StringBuilder();
        sb.append("flowchart TD\n");
        sb.append("  subgraph Cloud[\"☁️ MiniCloud Virtual Private Cloud\"]\n");

        for (Vpc vpc : vpcRepository.findAll()) {
            sb.append("    subgraph VPC_").append(sanitize(vpc.getId())).append("[\"🌐 ").append(escape(vpc.getName())).append(" (").append(vpc.getCidrBlock()).append(")\"]\n");
            for (Subnet sub : subnetRepository.findAll()) {
                if (sub.getVpcId() != null && sub.getVpcId().equals(vpc.getId())) {
                    sb.append("      subgraph SUB_").append(sanitize(sub.getId())).append("[\"🔒 Subnet ").append(escape(sub.getName())).append(" (").append(sub.getCidrBlock()).append(")\"]\n");
                    for (Instance inst : instanceRepository.findAll()) {
                        if (inst.getSubnetId() != null && inst.getSubnetId().equals(sub.getId())) {
                            sb.append("        INST_").append(sanitize(inst.getId())).append("[\"🖥️ ").append(escape(inst.getName())).append(" (").append(inst.getState()).append(")\"]\n");
                        }
                    }
                    sb.append("      end\n");
                }
            }
            sb.append("    end\n");
        }
        sb.append("  end\n\n");

        sb.append("  subgraph ServerlessAndStorage[\"⚡ Serverless & Storage Mesh\"]\n");
        for (Bucket b : bucketRepository.findAll()) {
            sb.append("    S3_").append(sanitize(b.getName())).append("[\"🪣 S3: ").append(escape(b.getName())).append("\"]\n");
        }
        for (Function f : functionRepository.findAll()) {
            sb.append("    FN_").append(sanitize(f.getName())).append("[\"λ Lambda: ").append(escape(f.getName())).append(" (").append(f.getRuntime()).append(")\"]\n");
        }
        for (SqsQueue q : sqsService.listQueues()) {
            sb.append("    SQS_").append(sanitize(q.getName())).append("[\"📬 SQS: ").append(escape(q.getName())).append("\"]\n");
        }
        for (SnsTopic t : snsService.listTopics()) {
            sb.append("    SNS_").append(sanitize(t.getName())).append("[\"📢 SNS: ").append(escape(t.getName())).append("\"]\n");
            for (var sub : t.getSubscriptions()) {
                if ("sqs".equalsIgnoreCase(sub.getProtocol())) {
                    String qName = sub.getEndpoint();
                    if (qName.contains(":")) qName = qName.substring(qName.lastIndexOf(":") + 1);
                    sb.append("    SNS_").append(sanitize(t.getName())).append(" -->|\"Fan-out\"| SQS_").append(sanitize(qName)).append("\n");
                }
            }
        }
        sb.append("  end\n");

        return sb.toString();
    }

    private String sanitize(Object id) {
        if (id == null) return "unknown";
        return id.toString().replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("\"", "\\\"");
    }
}
