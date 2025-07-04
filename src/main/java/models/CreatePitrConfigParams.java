package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import models.XClusterConfig;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import play.data.validation.Constraints;

@ApiModel(description = "Create PITR config parameters")
@NoArgsConstructor
public class CreatePitrConfigParams  {

  @JsonIgnore @Getter @Setter private UUID universeUUID;

  @JsonIgnore public UUID customerUUID;

  @ApiModelProperty(value = "PITR config name")
  public String name;

  @JsonIgnore public String keyspaceName;

  ///@JsonIgnore public TableType tableType;

  @ApiModelProperty(value = "Retention period of a snapshot")
  @Constraints.Required
  public long retentionPeriodInSeconds;

  @ApiModelProperty(value = "Time interval between snapshots")
  @Constraints.Required
  public long intervalInSeconds = 86400L;

  @JsonIgnore public XClusterConfig xClusterConfig;

  @JsonIgnore public boolean createdForDr = false;
}
