package models;
import static io.swagger.annotations.ApiModelProperty.AccessMode.READ_ONLY;
import static org.reflections.Reflections.log;
import static play.mvc.Http.Status.BAD_REQUEST;
import static play.mvc.Http.Status.UNAUTHORIZED;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.ebean.DuplicateKeyException;
import io.ebean.Finder;
import io.ebean.Model;
import io.ebean.annotation.DbArray;
import io.ebean.annotation.Encrypted;
import io.ebean.annotation.EnumValue;
import io.ebean.annotation.Transactional;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiModelProperty.AccessMode;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.data.validation.Constraints;
import play.mvc.Http.Status;

@Slf4j
@Entity
@ApiModel(description = "A user associated with a customer")
@Getter
@Setter
public class Users extends Model {

  public static final Logger LOG = LoggerFactory.getLogger(Users.class);


  /** These are the available user roles */
  public enum Role {
    @EnumValue("ConnectOnly")
    ConnectOnly,

    @EnumValue("ReadOnly")
    ReadOnly,

    @EnumValue("BackupAdmin")
    BackupAdmin,

    @EnumValue("Admin")
    Admin,

    @EnumValue("SuperAdmin")
    SuperAdmin;

    public String getFeaturesFile() {
      switch (this) {
        case Admin:
          return null;
        case ReadOnly:
          return "readOnlyFeatureConfig.json";
        case SuperAdmin:
          return null;
        case BackupAdmin:
          return "backupAdminFeatureConfig.json";
        case ConnectOnly:
          return "connectOnlyFeatureConfig.json";
        default:
          return null;
      }
    }

    public static Role union(Role r1, Role r2) {
      if (r1 == null) {
        return r2;
      }

      if (r2 == null) {
        return r1;
      }

      if (r1.compareTo(r2) < 0) {
        return r2;
      }

      return r1;
    }
  }

  public enum UserType {
    @EnumValue("local")
    local,

    @EnumValue("ldap")
    ldap,

    @EnumValue("oidc")
    oidc;
  }

  // A globally unique UUID for the Users.
  @Id
  @ApiModelProperty(value = "User UUID", accessMode = READ_ONLY)
  private UUID uuid = UUID.randomUUID();

  @ApiModelProperty(value = "Customer UUID", accessMode = READ_ONLY)
  private UUID customerUUID;

  @Constraints.Required
  @Constraints.Email
  @ApiModelProperty(
      value = "User email address",
      example = "username1@example.com",
      required = true)
  private String email;

  @JsonIgnore
  @ApiModelProperty(
      value = "User password hash",
      example = "$2y$10$ABccHWa1DO2VhcF1Ea2L7eOBZRhktsJWbFaB/aEjLfpaplDBIJ8K6")
  private String passwordHash;


  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  @ApiModelProperty(
      value = "User creation date",
      example = "2022-12-12T13:07:18Z",
      accessMode = READ_ONLY)
  private Date creationDate;

  @Encrypted @JsonIgnore private String authToken;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  @ApiModelProperty(
      value = "UI session token creation date",
      example = "2021-06-17T15:00:05Z",
      accessMode = READ_ONLY)
  private Date authTokenIssueDate;

  @ApiModelProperty(value = "Hash of API Token")
  @JsonIgnore
  private String apiToken;

  @JsonIgnore private Long apiTokenVersion = 0L;

  @ApiModelProperty(value = "User timezone")
  private String timezone;

  // The role of the user.
  @ApiModelProperty(value = "User role")
  private Role role;

  @ApiModelProperty(value = "True if the user is the primary user")
  private boolean isPrimary;

  @ApiModelProperty(value = "User Type")
  private UserType userType;

  @ApiModelProperty(value = "LDAP Specified Role")
  private boolean ldapSpecifiedRole;

  @Encrypted
  @Setter
  @ApiModelProperty(accessMode = AccessMode.READ_ONLY)
  private String oidcJwtAuthToken;

  @DbArray(name = "group_memberships")
  private Set<UUID> groupMemberships = new HashSet<>();

  public String getOidcJwtAuthToken() {
    return null;
  }

  @JsonIgnore
  public String getUnmakedOidcJwtAuthToken() {
    return oidcJwtAuthToken;
  }

  public static final Finder<UUID, Users> find = new Finder<UUID, Users>(Users.class) {};

  @Deprecated
  public static Users get(UUID userUUID) {
    return find.query().where().eq("uuid", userUUID).findOne();
  }

  public static Users getOrBadRequest(UUID userUUID) {
    Users user = get(userUUID);
    if (user == null) {
      throw new PlatformServiceException(BAD_REQUEST, "Invalid User UUID:" + userUUID);
    }
    return user;
  }

  public static Users getOrBadRequest(UUID customerUUID, UUID userUUID) {
    Users user = find.query().where().idEq(userUUID).eq("customer_uuid", customerUUID).findOne();
    if (user == null) {
      throw new PlatformServiceException(
          BAD_REQUEST,
          String.format("Invalid User UUID: '%s' for customer: '%s'.", userUUID, customerUUID));
    }
    return user;
  }

  public static List<Users> getAll(UUID customerUUID) {
    return find.query().where().eq("customer_uuid", customerUUID).findList();
  }

  public static List<Users> getAll() {
    return find.query().where().findList();
  }



  /**
   * Create new Users, we encrypt the password before we store it in the DB
   *
   * @param email
   * @param password
   * @return Newly Created Users
   */
  public static Users create(
      String email, String password, Role role, UUID customerUUID, boolean isPrimary) {
    try {
      return createInternal(email, password, role, customerUUID, isPrimary, UserType.local);
    } catch (DuplicateKeyException pe) {
      throw new PlatformServiceException(Status.CONFLICT, "User already exists");
    }
  }

  /**
   * Create new Users, we encrypt the password before we store it in the DB
   *
   * @return Newly Created Users
   */
  public static Users create(
      String email,
      String password,
      Role role,
      UUID customerUUID,
      boolean isPrimary,
      UserType userType) {
    try {
      return createInternal(email, password, role, customerUUID, isPrimary, userType);
    } catch (DuplicateKeyException pe) {
      throw new PlatformServiceException(Status.CONFLICT, "User already exists");
    }
  }

  /**
   * Create first Users associated to a customer, we encrypt the password before we store it in the
   * DB
   *
   * @return Newly Created Primary User
   */
  public static Users createPrimary(String email, String password, Role role, UUID customerUUID) {
    try {
      return createInternal(email, password, role, customerUUID, true, UserType.local);
    } catch (DuplicateKeyException pe) {
      throw new PlatformServiceException(Status.CONFLICT, "Customer already registered.");
    }
  }

  static Users createInternal(
      String email,
      String password,
      Role role,
      UUID customerUUID,
      boolean isPrimary,
      UserType userType) {
    Users users = new Users();
    users.save();
    return users;
  }

  /**
   * Delete Users identified via email
   *
   * @param email
   * @return void
   */
  public static void deleteUser(String email) {
    Users userToDelete = Users.find.query().where().eq("email", email).findOne();
  
    return;
  }

  /** Wrapper around save to make sure principal entity is created. */
  @Transactional
  @Override
  public void save() {
    super.save();
    Principal principal = Principal.get(this.uuid);
    if (principal == null) {
      //log.info("Adding Principal entry for user with email: " + this.email);
      //new Principal(this).save();
    }
  }

  /** Wrapper around delete to make sure principal entity is deleted. */
  @Transactional
  @Override
  public boolean delete() {
    log.info("Deleting Principal entry for user with email: " + this.email);
    Principal principal = Principal.getOrBadRequest(this.uuid);
    principal.delete();
    return super.delete();
  }

  /**
   * Validate if the email and password combination is valid, we use this to authenticate the Users.
   *
   * @param email
   * @param password
   * @return Authenticated Users Info
   */
  public static Users authWithPassword(String email, String password) {
    
    return null;
  }

  /**
   * Validate if the email and password combination is valid, we use this to authenticate the Users.
   *
   * @param email
   * @return Authenticated Users Info
   */
  public static Users getByEmail(String email) {
    if (email == null) {
      return null;
    }

    return Users.find.query().where().eq("email", email).findOne();
  }

  /**
   * Create a random auth token for the Users and store it in the DB.
   *
   * @return authToken
   */
  public String createAuthToken() {
    Date tokenExpiryDate = new DateTime().minusDays(1).toDate();
    if (authTokenIssueDate == null || authTokenIssueDate.before(tokenExpiryDate)) {
      SecureRandom randomGenerator = new SecureRandom();
      // Keeping the length as 128 bits.
      byte[] randomBytes = new byte[16];
      randomGenerator.nextBytes(randomBytes);
      // Converting to hexadecimal encoding
      authToken = new BigInteger(1, randomBytes).toString(16);
      authTokenIssueDate = new Date();
      save();
    }
    return authToken;
  }

  public void updateAuthToken(String authToken) {
    this.authToken = authToken;
    save();
  }

  /**
   * Create a random auth token without expiry date for Users and store it in the DB.
   *
   * @return apiToken
   */
  public String upsertApiToken() {
    return upsertApiToken(apiTokenVersion);
  }

  public String upsertApiToken(Long version) {
return null;
  }
}
