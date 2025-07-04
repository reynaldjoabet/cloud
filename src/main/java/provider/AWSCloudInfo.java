package provider;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableMap;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiModelProperty.AccessMode;
import io.swagger.annotations.ApiParam;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AWSCloudInfo implements CloudInfoInterface {

   @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.FIELD})
  public @interface EditableInUseProvider {
    public String name();

    public boolean allowed() default true;
  }

  private static final Map<String, String> configKeyMap =
      ImmutableMap.of(
          "awsAccessKeyID",
          "AWS_ACCESS_KEY_ID",
          "awsAccessKeySecret",
          "AWS_SECRET_ACCESS_KEY",
          "awsHostedZoneId",
          "HOSTED_ZONE_ID",
          "awsHostedZoneName",
          "HOSTED_ZONE_NAME");

  @JsonAlias("AWS_ACCESS_KEY_ID")
  @ApiModelProperty
  public String awsAccessKeyID;

  @JsonAlias("AWS_SECRET_ACCESS_KEY")
  @ApiModelProperty
  public String awsAccessKeySecret;

  @ApiModelProperty
  @EditableInUseProvider(name = "IMDSv2 Required", allowed = false)
  public Boolean useIMDSv2 = true;

  @JsonAlias("HOSTED_ZONE_ID")
  @ApiModelProperty
  @ApiParam(value = "Route 53 Zone ID")
  @EditableInUseProvider(name = "AWS Hosted Zone ID", allowed = false)
  public String awsHostedZoneId;

  @JsonAlias("HOSTED_ZONE_NAME")
  @ApiModelProperty
  @EditableInUseProvider(name = "AWS Hosted Zone Name", allowed = false)
  public String awsHostedZoneName;

  @ApiModelProperty(accessMode = AccessMode.READ_ONLY)
  @EditableInUseProvider(name = "AWS Host VPC Region", allowed = false)
  public String hostVpcRegion;

  @ApiModelProperty(accessMode = AccessMode.READ_ONLY)
  @EditableInUseProvider(name = "AWS Host VPC ID", allowed = false)
  public String hostVpcId;

  @ApiModelProperty(
      value = "New/Existing VPC for provider creation",
      accessMode = AccessMode.READ_ONLY)
  @EditableInUseProvider(name = "AWS VPC type", allowed = false)
  private VPCType vpcType = VPCType.EXISTING;

  @JsonIgnore
  public Map<String, String> getEnvVars() {
    Map<String, String> envVars = new HashMap<>();

    if (awsAccessKeyID != null) {
      envVars.put("AWS_ACCESS_KEY_ID", awsAccessKeyID);
      envVars.put("AWS_SECRET_ACCESS_KEY", awsAccessKeySecret);
    }
    if (awsHostedZoneId != null) {
      envVars.put("HOSTED_ZONE_ID", awsHostedZoneId);
    }
    if (awsHostedZoneName != null) {
      envVars.put("HOSTED_ZONE_NAME", awsHostedZoneName);
    }

    return envVars;
  }

  @JsonIgnore
  public Map<String, String> getConfigMapForUIOnlyAPIs(Map<String, String> config) {
    for (Map.Entry<String, String> entry : configKeyMap.entrySet()) {
      if (config.get(entry.getKey()) != null) {
        config.put(entry.getValue(), config.get(entry.getKey()));
        config.remove(entry.getKey());
      }
    }
    return config;
  }



  @JsonIgnore
  public void mergeMaskedFields(CloudInfoInterface providerCloudInfo) {
    AWSCloudInfo awsCloudInfo = (AWSCloudInfo) providerCloudInfo;
    // If the modify request contains masked value, overwrite those using
    // the existing ebean entity.
    if (this.awsAccessKeyID != null && this.awsAccessKeyID.contains("*")) {
      this.awsAccessKeyID = awsCloudInfo.awsAccessKeyID;
    }
    if (this.awsAccessKeySecret != null && this.awsAccessKeySecret.contains("*")) {
      this.awsAccessKeySecret = awsCloudInfo.awsAccessKeySecret;
    }
    if (this.awsHostedZoneName == null && awsCloudInfo.awsHostedZoneName != null) {
      this.awsHostedZoneName = awsCloudInfo.awsHostedZoneName;
    }
  }

  @Override
  public void withSensitiveDataMasked() {
    
  }
}
