package models;
import static io.swagger.annotations.ApiModelProperty.AccessMode.READ_ONLY;
import static play.mvc.Http.Status.BAD_REQUEST;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import io.ebean.DB;
import io.ebean.ExpressionList;
import io.ebean.Finder;
import io.ebean.Model;
import io.ebean.PersistenceContextScope;
import io.ebean.annotation.DbJson;
import io.ebean.annotation.WhenModified;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import play.mvc.Http.Status;
import filters.NodeAgentFilter;

@Slf4j
@Entity
@Getter
@Setter
@ApiModel(description = "Node agent details")
public class NodeAgent extends Model {
  
  public static final String NODE_AGENT_DIR = "node-agent";

  private static final Set<State> INACTIVE_STATES =
      ImmutableSet.of(State.REGISTERING, State.REGISTERED);

  /** Node agent server OS type. */
  public enum OSType {
    DARWIN,
    LINUX;

    public static OSType parse(String strType) {
      OSType osType = EnumUtils.getEnumIgnoreCase(OSType.class, strType);
      if (osType == null) {
        throw new IllegalArgumentException("Unknown OS type: " + strType);
      }
      return osType;
    }
  }

  /** Node agent server arch type. */
  public enum ArchType {
    ARM64("aarch64"),
    AMD64("x86_64");

    private final Set<String> aliases;

    private ArchType(String... aliases) {
      this.aliases =
          aliases == null
              ? Collections.emptySet()
              : Collections.unmodifiableSet(
                  Arrays.stream(aliases).map(String::toLowerCase).collect(Collectors.toSet()));
    }

    public static ArchType parse(String strType) {
      String lower = strType.toLowerCase();
      for (ArchType archType : EnumSet.allOf(ArchType.class)) {
        if (archType.name().equalsIgnoreCase(lower) || archType.aliases.contains(lower)) {
          return archType;
        }
      }
      throw new IllegalArgumentException("Unknown arch type: " + strType);
    }
  }

  /** State and the transitions. */
  public enum State {
    REGISTERING {
      @Override
      public Set<State> nextStates() {
        return toSet(READY, REGISTERED);
      }
    },
    REGISTERED {
      @Override
      public Set<State> nextStates() {
        return toSet(READY);
      }
    },
    UPGRADE {
      @Override
      public Set<State> nextStates() {
        return toSet(UPGRADED);
      }
    },
    UPGRADED {
      @Override
      public Set<State> nextStates() {
        return toSet(READY);
      }
    },
    READY {
      @Override
      public Set<State> nextStates() {
        return toSet(READY, UPGRADE);
      }
    };

    public abstract Set<State> nextStates();

    public static State parse(String strType) {
      State state = EnumUtils.getEnumIgnoreCase(State.class, strType);
      if (state == null) {
        throw new IllegalArgumentException("Unknown state: " + state);
      }
      return state;
    }

    private static Set<State> toSet(State... states) {
      return states == null
          ? Collections.emptySet()
          : ImmutableSet.<State>builder().add(states).build();
    }

    public void validateTransition(State nextState) {
      if (!this.nextStates().contains(nextState)) {
        throw new IllegalStateException(
            String.format("Invalid state transition from %s to %s", name(), nextState.name()));
      }
    }
  }

  @Getter
  @Setter
  public static class Config {
    private String certPath;
    private String serverCert;
    private String serverKey;
    private boolean offloadable;
  }

  public static final Finder<UUID, NodeAgent> finder =
      new Finder<UUID, NodeAgent>(NodeAgent.class) {};

  public static final String ROOT_CA_CERT_NAME = "ca.root.crt";
  public static final String ROOT_CA_KEY_NAME = "ca.key.pem";
  public static final String SERVER_CERT_NAME = "server.crt";
  public static final String SERVER_KEY_NAME = "server.key";
  public static final String MERGED_ROOT_CA_CERT_NAME = "merged.ca.key.crt";

  @Id
  @ApiModelProperty(value = "Node agent UUID", accessMode = READ_ONLY)
  private UUID uuid;

  @ApiModelProperty(value = "Node agent name", accessMode = READ_ONLY)
  private String name;

  @ApiModelProperty(value = "Node agent server IP", accessMode = READ_ONLY)
  private String ip;

  @ApiModelProperty(value = "Node agent server port", accessMode = READ_ONLY)
  private int port;

  @ApiModelProperty(value = "Customer UUID", accessMode = READ_ONLY)
  private UUID customerUuid;

  @ApiModelProperty(value = "Node agent installed version", accessMode = READ_ONLY)
  private String version;

  @Enumerated(EnumType.STRING)
  @ApiModelProperty(value = "Node agent state", accessMode = READ_ONLY)
  @Setter(AccessLevel.NONE)
  private State state;

  @WhenModified
  @Column(nullable = false)
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  @ApiModelProperty(
      value = "Updated time",
      accessMode = READ_ONLY,
      example = "2022-12-21T13:07:18Z")
  private Date updatedAt;

  @ApiModelProperty(value = "Node agent configuration", accessMode = READ_ONLY)
  @Column(nullable = false)
  @DbJson
  private Config config;

  @Enumerated(EnumType.STRING)
  @ApiModelProperty(value = "Node agent host OS", accessMode = READ_ONLY)
  private OSType osType;

  @Enumerated(EnumType.STRING)
  @ApiModelProperty(value = "Node agent host machine arch", accessMode = READ_ONLY)
  private ArchType archType;

  @ApiModelProperty(value = "Node agent installation directory", accessMode = READ_ONLY)
  @Column(nullable = false)
  private String home;





  public static Optional<NodeAgent> maybeGet(UUID uuid) {
    return Optional.ofNullable(finder.byId(uuid));
  }

  public static Optional<NodeAgent> maybeGetByIp(String ip) {
    return finder.query().where().eq("ip", ip).findOneOrEmpty();
  }

  public static NodeAgent getOrBadRequest(UUID customerUuid, UUID nodeAgentUuid) {
    NodeAgent nodeAgent =
        finder.query().where().eq("customer_uuid", customerUuid).idEq(nodeAgentUuid).findOne();
    if (nodeAgent == null) {
      throw new PlatformServiceException(BAD_REQUEST, "Cannot find node agent " + nodeAgentUuid);
    }
    return nodeAgent;
  }

  public static NodeAgent getOrBadRequest(UUID nodeAgentUuid) {
    return NodeAgent.maybeGet(nodeAgentUuid)
        .orElseThrow(
            () ->
                new PlatformServiceException(
                    BAD_REQUEST, "Cannot find node agent " + nodeAgentUuid));
  }

  public static Collection<NodeAgent> list(UUID customerUuid, String nodeAgentIp /* Optional */) {
    ExpressionList<NodeAgent> expr = finder.query().where().eq("customer_uuid", customerUuid);
    if (StringUtils.isNotBlank(nodeAgentIp)) {
      expr = expr.eq("ip", nodeAgentIp);
    }
    return expr.findList();
  }

  public static Set<NodeAgent> getAll(UUID customerUuid) {
    return finder.query().where().eq("customer_uuid", customerUuid).findSet();
  }

  public static Set<NodeAgent> getAll() {
    return finder.query().findSet();
  }

  
  public static Set<NodeAgent> getUpdatableNodeAgents(UUID customerUuid, String softwareVersion) {
    return finder
        .query()
        .where()
        .eq("customer_uuid", customerUuid)
        .ne("version", softwareVersion)
        .findSet();
  }

  

  public static void delete(UUID uuid) {
    finder.deleteById(uuid);
  }

  


  @JsonIgnore
  public byte[] getServerCert() {
    Path serverCertPath = null;//getCertDirPath().resolve(SERVER_CERT_NAME);
    Objects.requireNonNull(serverCertPath, "Server cert must exist");
    try {
      return Files.readAllBytes(serverCertPath);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @JsonIgnore
  public byte[] getServerKey() {
    Path serverKeyPath =null; //getCertDirPath().resolve(SERVER_KEY_NAME);
    Objects.requireNonNull(serverKeyPath, "Server key must exist");
    try {
      return Files.readAllBytes(serverKeyPath);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

 
  @JsonIgnore
  public PublicKey getPublicKey() {
    try {
      X509Certificate cert = getServerX509Cert();
      return cert.getPublicKey();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  @JsonIgnore
  public X509Certificate getServerX509Cert() {
    try {
      CertificateFactory factory = CertificateFactory.getInstance("X.509");
      return (X509Certificate)
          factory.generateCertificate(new ByteArrayInputStream(getServerCert()));
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }
  
}
