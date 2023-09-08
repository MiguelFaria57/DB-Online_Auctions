/*
 * João Melo, 2019216747
 * João Monteiro, 2019216764
 * Miguel Faria, 2019216809
 */

package pt.uc.dei.bd2021;

import java.sql.*;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@SuppressWarnings("SqlResolve")
@RestController
public class BDDemo {
    byte[] key = Base64.getDecoder().decode("ccOkgNV66XjHggfoMYVEC6U3fZQIqeob4rO0FjXtOJ8=");
    private static final Logger logger = LoggerFactory.getLogger(BDDemo.class);

    public User validateToken(String token){
        String [] tokens = token.split(" ");
        User user = new User();
        user.setValid(true);
        if(tokens[0].equals("Bearer")){
            Jws<Claims> result = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(key))
                    .build()
                    .parseClaimsJws(tokens[1]);

            if(result.getBody().getSubject() == null || result.getBody().getAudience() == null)
                user.setValid(false);

            else{
                user.setUserID(Integer.parseInt(result.getBody().getSubject()));
                user.setUserType(result.getBody().getAudience());}
        }
        else{
            user.setValid(false);
        }
        return user;
    }


    public static String encrypt(String password, int tamanho){
        StringBuilder encriptado = new StringBuilder();

        for (int i=0; i<password.length(); i++)
        {
            if (Character.isUpperCase(password.charAt(i)))
            {
                char ch = (char)(((int)password.charAt(i) +
                        tamanho - 65) % 26 + 65);
                encriptado.append(ch);
            }
            else
            {
                char ch = (char)(((int)password.charAt(i) +
                        tamanho - 97) % 26 + 97);
                encriptado.append(ch);
            }
        }
        String a = "" + encriptado;
        return a;
    }


    public boolean validate_user_pass (String username, String pass){

        for(int i = 0; i<username.length(); i++) {
            if (       ((int) username.charAt(i) < 48 || (int) username.charAt(i) > 57)
                    && ((int) username.charAt(i) < 65 || (int) username.charAt(i) > 90)
                    && ((int) username.charAt(i) < 97 || (int) username.charAt(i) > 122)
                    && ((int) username.charAt(i) != 95)) {
                return false;
            }
        }

        for(int j = 0; j<pass.length(); j++) {
            if (       ((int) pass.charAt(j) < 65 || (int) pass.charAt(j) > 90)
                    && ((int) pass.charAt(j) < 97 || (int) pass.charAt(j) > 122)) {
                return false;
            }

        }
        return true;
    }

    @GetMapping("/dbproj")
    public String landing() {
        logger.info("### Landing /dbproj - Welcome");
        return "Welcome to the Online Auction House!";
    }

    /**
     * POST
     * Registo de users
     */

    @PostMapping(value = "/dbproj/user", consumes = "application/json")
    @ResponseBody
    public String createUser(@RequestBody Map<String, Object> payload) {
        logger.info("### POST /dbproj/user - Registo de utlizadores");
        logger.debug("- New User");
        logger.debug("Payload: {}", payload);
        String erro = "Failed";
        Connection conn = RestServiceApplication.getConnection();
        if (conn == null) {
            logger.debug("DB Problem");
            return "DB Problem";
        }
        if(validate_user_pass((String) payload.get("user_name"),(String) payload.get("user_password")) ){
            try (PreparedStatement ps = conn.prepareStatement(""
                    + "INSERT INTO utilizador (user_id,user_name,user_email,user_password,user_ban,user_type)"
                    + "VALUES (?,?,?,?,?,?)")) {
                PreparedStatement ps2 = conn.prepareStatement("SELECT max(user_id) AS u_id FROM utilizador");
                ResultSet rows = ps2.executeQuery();
                int user_id;
                if (rows.next()) {
                    user_id = rows.getInt("u_id") + 1;
                } else {
                    user_id = 1;
                }
                ps.setInt(1, user_id);
                ps.setString(2, (String) payload.get("user_name"));
                ps.setString(3, (String) payload.get("user_email"));
                ps.setString(3, (String) payload.get("user_email"));
                String pass = encrypt((String) payload.get("user_password"),18);
                ps.setString(4, pass);
                ps.setBoolean(5, false);
                ps.setString(6, "user");
                int affectedRows = ps.executeUpdate();
                conn.commit();
                if (affectedRows == 1) {
                    logger.debug("User Inserted");
                    return "User Inserted\nuser_id: " + user_id;
                } else {
                    logger.debug("Error inserting user");
                }
            } catch (SQLException ex) {
                logger.error("Error in DB", ex);
                try {
                    conn.rollback();
                } catch (SQLException ex1) {
                    logger.warn("Couldn't rollback", ex);
                }
            } catch (ClassCastException exception) {
                logger.error("Error Casting", exception);
                erro = "One of the values has been sent incorrectly";
                try {
                    conn.rollback();
                } catch (SQLException ex1) {
                    logger.warn("Couldn't rollback", ex1);
                }
            } finally {
                try {
                    conn.close();
                } catch (SQLException ex) {
                    logger.error("Error in DB", ex);
                }
            }
        }
        else{
            erro = "Special caracters are not allowed with the exception of '_' in the username.\n" +
                    "In the password only insert lower or upper case letters";
        }
        return erro;
    }


    /**
     * PUT
     * Autenticação de utilizadores
     */

    @PutMapping(value = "/dbproj/authentication", consumes = "application/json")
    @ResponseBody
    public Map<String, Object> authenticateUser(@RequestBody Map<String, Object> payload){
        logger.info("### PUT /dbproj/authentication - Autenticação de utilizadores");
        logger.debug("- Authentication");
        logger.debug("Payload: {}", payload);

        Map<String,Object> content = new HashMap<>();
        Connection conn = RestServiceApplication.getConnection();
        if (conn == null) {
            logger.debug("DB Problem");
            return content;
        }

        try (PreparedStatement ps = conn.prepareStatement("SELECT user_id,user_type,user_ban FROM utilizador WHERE user_name = ? AND user_password = ?")){
            ps.setString(1, (String) payload.get("username"));
            String passEnc = encrypt((String) payload.get("password"),18);
            ps.setString(2,passEnc);
            ResultSet rows = ps.executeQuery();
            if(rows.next()){
                if(!rows.getBoolean("user_ban")){
                    Instant atual = Instant.now();
                    String jwt = Jwts.builder()
                            .setSubject("" + rows.getInt("user_id"))
                            .setAudience(rows.getString("user_type"))
                            .setIssuedAt(Date.from(atual))
                            .setExpiration(Date.from(atual.plus(2, ChronoUnit.DAYS)))
                            .signWith(Keys.hmacShaKeyFor(key))
                            .compact();

                    content.put("AuthToken",jwt);
                    return content;
                }else{
                    content.put("Couldn't log-in because you have been banned by an administrator","");
                }
            }

            else{
                logger.debug("Username or Password incorrect");
            }
            conn.commit();
        } catch (SQLException ex) {
            logger.error("Error in DB", ex);
            try {
                conn.rollback();
            } catch (SQLException ex1) {
                logger.warn("Couldn't rollback", ex);
            }
        }catch (ClassCastException exception){
            logger.error("Error Casting", exception);
            try {
                conn.rollback();
            } catch (SQLException ex1) {
                logger.warn("Couldn't rollback", ex1);
            }
        } finally {
            try {
                conn.close();
            } catch (SQLException ex) {
                logger.error("Error in DB", ex);
            }
        }
        content.put("Username ou Password Incorreto(a)","AuthError");
        return content;
    }


    /**
     * GET
     * Listar todos	os users existentes
     * Só para testes
     */

    @GetMapping(value = "/dbproj/users", produces = "application/json")
    @ResponseBody
    public List<Map<String, Object>> getAllUsers(@RequestHeader(value = "Authorization",required = false)String token) {
        logger.info("### GET /dbproj/users - Listar todos os users existentes");
        logger.debug("- All Users");

        List<Map<String, Object>> payload = new ArrayList<>();
        if(token == null){
            logger.debug("Didn't get any token");
            Map<String, Object> content = new HashMap<>();
            content.put("Didn't get any token","");
            payload.add(content);
            return payload;
        }
        User user = validateToken(token);
        if (user.isValid()) {
            Connection conn = RestServiceApplication.getConnection();
            if (conn == null) {
                logger.debug("DB Problem");
                return payload;
            }

            try (Statement stmt = conn.createStatement()) {
                ResultSet rows = stmt.executeQuery("SELECT user_id,user_name,user_password,user_ban,user_type " +
                        "FROM utilizador");
                boolean flag = true;
                while (rows.next()) {
                    flag = false;
                    Map<String, Object> content = new HashMap<>();
                    logger.debug("'user_id': {}, 'user_name': {}, 'user_type': {}",
                            rows.getInt("user_id"), rows.getString("user_name"), rows.getString("user_type")
                    );
                    content.put("Número do User", rows.getInt("user_id"));
                    content.put("Nome do User", rows.getString("user_name"));
                    content.put("Tipo", rows.getString("user_type"));
                    payload.add(content);
                }
                if (flag) {
                    logger.debug("Error in DB");
                }
            } catch (SQLException ex) {
                logger.error("Error in DB", ex);
            }
        }
        else{
            logger.debug("Couldn't validate token");
            Map<String, Object> content = new HashMap<>();
            content.put("Couldn't validate token.","");
            payload.add(content);

        }
        return payload;
    }


    /**
     * POST
     * Criar um	novo leilão
     */

    @PostMapping(value = "/dbproj/leilao", consumes = "application/json")
    @ResponseBody
    public String createLeilao(@RequestBody Map<String, Object> payload,@RequestHeader(value = "Authorization",required = false)String token) {
        logger.info("### POST /dbproj/leilao - Criar um novo leilão");
        logger.debug("- New Leilao");
        logger.debug("Payload: {}", payload);
        String erro = "Failed";
        Connection conn = RestServiceApplication.getConnection();
        if (conn == null) {
            logger.debug("DB Problem");
            return "DB Problem";
        }
        if(token == null){
            logger.debug("Didn't get any token");
            return "Didn't get any token";
        }
        User user = validateToken(token);
        if (user.isValid()) {
            try (PreparedStatement ps = conn.prepareStatement(""
                    + "INSERT INTO leilao_artigo (leilao_id,leilao_end_date,leilao_minprice,leilao_currentbid,leilao_authorized,artigo_artigo_id,artigo_artigo_name,artigo_artigo_description,utilizador_user_id)"
                    + "VALUES (?,?,?,?,?,?,?,?,?)")) {
                PreparedStatement ps2 = conn.prepareStatement("SELECT max(leilao_id) AS l_id FROM leilao_artigo");
                ResultSet rows = ps2.executeQuery();
                int leilao_id;
                if(rows.next()){
                    leilao_id =  rows.getInt("l_id")+1;
                }
                else{
                    leilao_id = 1;
                }
                String ean = "";
                String ALPHA_NUMERIC_STRING = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
                for (int i=0; i<10; i++) {
                    int character = (int)(Math.random()*ALPHA_NUMERIC_STRING.length());
                    ean += (ALPHA_NUMERIC_STRING.charAt(character));
                }
                System.out.println(ean);

                SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss");
                java.util.Date utilStartDate = dateFormat.parse((String) payload.get("leilao_end_date"));
                java.sql.Date sqlStartDate = new java.sql.Date(utilStartDate.getTime());
                Timestamp timestamp = new java.sql.Timestamp(sqlStartDate.getTime());

                Timestamp timestampAtual = new Timestamp(System.currentTimeMillis());
                SimpleDateFormat current_date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                if (timestamp.compareTo(Timestamp.valueOf(current_date.format(timestampAtual))) < 0) {
                    logger.debug("This date is invalid");
                    return "This date is invalid";
                }
                else {
                    ps.setInt(1, leilao_id);
                    ps.setTimestamp(2, timestamp);
                    ps.setObject(3, payload.get("leilao_minprice"), java.sql.Types.FLOAT);
                    ps.setObject(4, 0);
                    ps.setBoolean(5, true);
                    ps.setString(6, ean);
                    ps.setString(7, (String) payload.get("artigo_artigo_name"));
                    ps.setString(8, (String) payload.get("artigo_artigo_description"));
                    ps.setInt(9, user.getUserID());
                    int affectedRows = ps.executeUpdate();
                    conn.commit();
                    if (affectedRows == 1) {
                        logger.debug("Leilao Created");
                        return "Leilao Created\nleilao_id: " + leilao_id;
                    } else {
                        logger.debug("Error creating Leilao");
                    }
                }
            } catch (SQLException | ParseException ex) {
                logger.error("Error in DB", ex);
                try {
                    conn.rollback();
                } catch (SQLException ex1) {
                    logger.warn("Couldn't rollback", ex);
                }
            }catch (ClassCastException exception){
                logger.error("Error Casting", exception);
                erro = "One of the values has been sent incorrectly";
                try {
                    conn.rollback();
                } catch (SQLException ex1) {
                    logger.warn("Couldn't rollback", ex1);
                }
            } finally {
                try {
                    conn.close();
                } catch (SQLException ex) {
                    logger.error("Error in DB", ex);
                }
            }

        }
        else{
            logger.debug("Couldn't validate token");
            erro = "Couldn't validate token.";
        }
        return erro;
    }


    /**
     * GET
     * Listar todos	os leilões existentes
     */

    @GetMapping(value = "/dbproj/leiloes", produces = "application/json")
    @ResponseBody
    public List<Map<String, Object>> getAllLeiloes(@RequestHeader(value = "Authorization",required = false)String token) {
        logger.info("### GET /dbproj/leiloes - Listar todos os leilões existentes");
        logger.debug("- All Leiloes");

        List<Map<String, Object>> payload = new ArrayList<>();
        Connection conn = RestServiceApplication.getConnection();
        if (conn == null) {
            logger.debug("DB Problem");
            return payload;
        }
        if(token == null){
            logger.debug("Didn't get any token");
            Map<String, Object> content = new HashMap<>();
            content.put("Didn't get any token","");
            payload.add(content);
            return payload;
        }

        User user = validateToken(token);
        if (user.isValid()) {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rows = stmt.executeQuery("SELECT leilao_id, artigo_artigo_description, leilao_end_date, leilao_ended, leilao_authorized " +
                        "FROM leilao_artigo");
                boolean flag = true;
                while (rows.next()) {

                    Timestamp currenttime = new Timestamp(System.currentTimeMillis());
                    SimpleDateFormat current_date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                    if (rows.getTimestamp("leilao_end_date").compareTo(Timestamp.valueOf(current_date.format(currenttime))) > 0 && !rows.getBoolean("leilao_ended") && rows.getBoolean("leilao_authorized")) {
                        flag = false;
                        Map<String, Object> content = new HashMap<>();
                        logger.debug("'leilao_id': {}, 'artigo_artigo_description': {}",
                                rows.getInt("leilao_id"), rows.getString("artigo_artigo_description")
                        );
                        content.put("Número do leilão", rows.getInt("leilao_id"));
                        content.put("Descrição do artigo", rows.getString("artigo_artigo_description"));

                        payload.add(content);
                    }
                }
                if (flag) {
                    logger.debug("There are no Leiloes");
                }
            } catch (SQLException ex) {
                logger.error("Error in DB", ex);
            }
        }
        else{
            logger.debug("Couldn't validate token");
            Map<String, Object> content = new HashMap<>();
            content.put("Couldn't validate token.","");
            payload.add(content);
        }
        return payload;
    }


    /**
     * GET
     * Pesquisar leilões existentes
     */

    @GetMapping(value = "/dbproj/leiloes/{keyword}", produces = "application/json")
    @ResponseBody
    public List<Map<String, Object>> getLeiloesKeyword(@PathVariable("keyword") String leilao_keyword,@RequestHeader(value = "Authorization",required = false)String token) {
        logger.info("### GET /dbproj/leilao/keyword - Pesquisar leilões existentes");
        logger.debug("- Selected leiloes (keyword): {}", leilao_keyword);

        List<Map<String, Object>> payload = new ArrayList<>();
        Connection conn = RestServiceApplication.getConnection();
        if (conn == null) {
            logger.debug("DB Problem");
        }
        if(token == null){
            logger.debug("Didn't get any token");
            Map<String, Object> content = new HashMap<>();
            content.put("Didn't get any token","");
            payload.add(content);
            return payload;
        }

        User user = validateToken(token);
        if (user.isValid()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * " +
                    "FROM leilao_artigo " +
                    "WHERE artigo_artigo_id " +
                    "LIKE ? OR artigo_artigo_description LIKE ?")) {
                ps.setString(1, "%" + leilao_keyword + "%");
                ps.setString(2, "%" + leilao_keyword + "%");
                ResultSet rows = ps.executeQuery();
                boolean flag = true;
                while (rows.next()) {
                    Timestamp currenttime = new Timestamp(System.currentTimeMillis());
                    SimpleDateFormat current_date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                    if (rows.getTimestamp("leilao_end_date").compareTo(Timestamp.valueOf(current_date.format(currenttime))) > 0 && !rows.getBoolean("leilao_ended") && rows.getBoolean("leilao_authorized")) {
                        flag = false;
                        Map<String, Object> content = new HashMap<>();
                        logger.debug("'artigo_artigo_id': {}, 'artigo_artigo_description': {}",
                                rows.getString("artigo_artigo_id"), rows.getString("artigo_artigo_description"));
                        content.put("Número do artigo", rows.getString("artigo_artigo_id"));
                        content.put("Descrição do artigo", rows.getString("artigo_artigo_description"));
                        payload.add(content);
                    }
                }
                if (flag) {
                    logger.debug("There are no Leiloes with the given keyword");
                }
            } catch (SQLException ex) {
                logger.error("Error in DB", ex);
            }
        }
        else{
            logger.debug("Couldn't validate token");
            Map<String, Object> content = new HashMap<>();
            content.put("Couldn't validate token.","");
            payload.add(content);
        }
        return payload;
    }


    /**
     * GET
     * Consultar detalhes de um	leilão
     */

    @GetMapping(value = "/dbproj/leilao/{leilao_id}", produces = "application/json")
    @ResponseBody
    public Map<String, Object> getLeilaoId(@PathVariable("leilao_id") int leilao_id, @RequestHeader(value = "Authorization",required = false)String token) {
        logger.info("### GET /dbproj/leiloes/leilao_id - Consultar detalhes de um leilão");
        logger.debug("- Selected leilao (id): {}", leilao_id);

        Map<String, Object> content = new HashMap<>();
        Connection conn = RestServiceApplication.getConnection();
        if (conn == null) {
            logger.debug("DB Problem");
            return content;
        }

        if(token == null){
            logger.debug("Didn't get any token");
            content.put("Didn't get any token","");
            return content;
        }
        User user = validateToken(token);
        if (user.isValid()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM leilao_artigo WHERE leilao_id = ?")) {
                ps.setInt(1, leilao_id);
                ResultSet rows = ps.executeQuery();
                if (rows.next()) {
                    logger.debug("'leilao_id': {}, 'leilao_end_date': {}, 'leilao_minprice': {}, 'leilao_currentbid':{}, " +
                                    "'leilao_authorized':{}, 'artigo_artigo_id':{}, 'artigo_artigo_name': {}, " +
                                    "'artigo_artigo_description': {}, 'utilizador_user_id': {}",
                            rows.getInt("leilao_id"), rows.getString("leilao_end_date"),
                            rows.getFloat("leilao_minprice"), rows.getFloat("leilao_currentbid"),
                            rows.getBoolean("leilao_authorized"), rows.getString("artigo_artigo_id"),
                            rows.getString("artigo_artigo_name"), rows.getString("artigo_artigo_description"),
                            rows.getInt("utilizador_user_id")
                    );
                    content.put("Número do leilão", rows.getInt("leilao_id"));
                    content.put("Data final do leilão", rows.getString("leilao_end_date"));
                    content.put("Preço mínimo do leilão", rows.getFloat("leilao_minprice"));
                    content.put("Preço atual do leilão", rows.getFloat("leilao_currentbid"));
                    content.put("Leilão autorizado:", rows.getBoolean("leilao_authorized"));
                    content.put("Número do artigo", rows.getString("artigo_artigo_id"));
                    content.put("Nome do artigo", rows.getString("artigo_artigo_name"));
                    content.put("Descrição do artigo", rows.getString("artigo_artigo_description"));
                    content.put("Número do User criador do leilão", rows.getInt("utilizador_user_id"));

                    // check for messages in mural
                    PreparedStatement ps1 = conn.prepareStatement("SELECT message " +
                            "FROM mural " +
                            "WHERE leilao_artigo_leilao_id = ?");
                    ps1.setInt(1, leilao_id);
                    ResultSet rows1 = ps1.executeQuery();
                    boolean flag1 = true;
                    String string = "";
                    while (rows1.next()) {
                        flag1 = false;
                        string += rows1.getString("message") + " | ";
                    }
                    if (flag1) {
                        logger.debug("There are no messages in mural for this leilao");
                    }
                    else {
                        content.put("Mural", string);
                    }

                    //  check for licitacoes
                    PreparedStatement ps2 = conn.prepareStatement("SELECT licitacao_bid " +
                            "FROM licitacao " +
                            "WHERE leilao_artigo_leilao_id = ?");
                    ps2.setInt(1, leilao_id);
                    ResultSet rows2 = ps2.executeQuery();
                    boolean flag2 = true;
                    String string2 = "";
                    while (rows2.next()) {
                        flag2 = false;
                        string2 += rows2.getFloat("licitacao_bid") + " | ";
                    }
                    if (flag2) {
                        logger.debug("There are no Licitacoes for this Leilao");
                    }
                    else {
                        content.put("Licitacoes", string2);
                    }

                } else {
                    logger.debug("There is no Leilao with the given ID");
                }
            } catch (SQLException ex) {
                logger.error("Error in DB", ex);
            }
        }
        else{
            logger.debug("Couldn't validate token");
            content.put("Couldn't validate token.","");
        }
        return content;
    }


    /**
     * GET
     * Listar todos os leilões em que o	utilizador tenha atividade
     */

    @GetMapping(value = "/dbproj/leiloes/user", produces = "application/json")
    @ResponseBody
    public List<Map<String, Object>> getLeiloesUser( @RequestHeader(value = "Authorization",required = false)String token) {
        logger.info("### GET /dbproj/leiloes/user - Listar todos os leilões em que o utilizador tenha atividade");


        List<Map<String, Object>> payload = new ArrayList<>();
        Connection conn = RestServiceApplication.getConnection();
        if (conn == null) {
            logger.debug("DB Problem");
            return payload;
        }

        if(token == null){
            logger.debug("Didn't get any token");
            Map<String, Object> content = new HashMap<>();
            content.put("Didn't get any token.","");
            payload.add(content);
        }
        User user = validateToken(token);
        if (user.isValid()) {
            try (PreparedStatement ps2 = conn.prepareStatement("SELECT leilao_artigo.leilao_id " +
                    "FROM leilao_artigo " +
                    "WHERE leilao_artigo.utilizador_user_id = ? " +
                    "UNION SELECT licitacao.leilao_artigo_leilao_id " +
                    "FROM licitacao " +
                    "WHERE licitacao.utilizador_user_id = ?")) {
                ps2.setInt(1, user.getUserID());
                ps2.setInt(2, user.getUserID());
                ResultSet leilao_id = ps2.executeQuery();

                boolean flag = true;
                while (leilao_id.next()) {
                    flag = false;
                    try (PreparedStatement ps3 = conn.prepareStatement("SELECT DISTINCT leilao_id, leilao_end_date, leilao_currentbid, artigo_artigo_name " +
                            "FROM leilao_artigo " +
                            "WHERE leilao_id = ?")) {
                        int l_id = leilao_id.getInt("leilao_id");
                        ps3.setInt(1, l_id);

                        ResultSet rows = ps3.executeQuery();
                        if (rows.next()) {
                            Map<String, Object> content = new HashMap<>();
                            logger.debug("'leilao_id': {}, 'leilao_end_date': {}, 'leilao_currentbid':{}, 'artigo_artigo_name': {}",
                                    rows.getInt("leilao_id"), rows.getTimestamp("leilao_end_date"),
                                    rows.getFloat("leilao_currentbid"), rows.getString("artigo_artigo_name")
                            );
                            content.put("Número do leilão", rows.getInt("leilao_id"));
                            content.put("Data final do leilão", rows.getString("leilao_end_date"));
                            content.put("Preço atual do leilão", rows.getFloat("leilao_currentbid"));
                            content.put("Nome do artigo", rows.getString("artigo_artigo_name"));
                            payload.add(content);
                        } else {
                            logger.debug("There are no Leiloes with the given user_name");
                        }
                    } catch (SQLException ex) {
                        logger.error("Error in DB", ex);
                    }
                }
                if (flag) {
                    logger.debug("There are no Leiloes with the given user_name");
                }
            } catch (SQLException ex) {
                logger.error("Error in DB", ex);
            }
        }
        else{
            logger.debug("Couldn't validate token");
            Map<String, Object> content = new HashMap<>();
            content.put("Couldn't validate token.","");
            payload.add(content);
        }
        return payload;
    }


    /**
     * PUT
     * Efetuar uma licitação num leilão
     */

    @PutMapping(value = "/dbproj/licitar", consumes = "application/json")
    @ResponseBody
    public String putLicitacao(@RequestBody Map<String, Object> payload,@RequestHeader(value = "Authorization",required = false)String token) {
        logger.info("### PUT /dbproj/licitar - Efetuar uma licitação num leilão");
        logger.debug("- Place bid ");
        logger.debug("Payload: {}", payload);

        String erro = "Failed";
        Connection conn = RestServiceApplication.getConnection();
        if (conn == null) {
            logger.debug("DB Problem");
            return "DB Problem";
        }

        if(token == null){
            logger.debug("Didn't get any token");
            return "Didn't get any token";
        }
        User user = validateToken(token);
        if (user.isValid()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT leilao_currentbid, leilao_end_date, leilao_minprice, leilao_authorized " +
                    "FROM leilao_artigo " +
                    "WHERE leilao_id = ?")) {
                ps.setInt(1, (int) payload.get("leilao_id"));

                ResultSet rows = ps.executeQuery();

                Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                SimpleDateFormat current_date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                double bid = (double) payload.get("licitacao");

                if (rows.next()) {
                    if(!rows.getBoolean("leilao_authorized")){
                        logger.debug("This auction has been banned");
                        return "This auction has been banned";
                    }
                    if (rows.getFloat("leilao_currentbid") >= bid) {
                        logger.debug("This bid is not high enough");
                        return "This bid is not high enough";
                    }
                    if (rows.getFloat("leilao_minprice") > bid) {
                        logger.debug("This bid is lower than the minprice");
                        return "This bid is lower than the minprice";
                    } else if (rows.getTimestamp("leilao_end_date").compareTo(Timestamp.valueOf(current_date.format(timestamp))) < 0) {
                        logger.debug("This Leilao has already finished");
                        return "This Leilao has already finished";
                    } else {
                        PreparedStatement ps2 = conn.prepareStatement("SELECT max(licitacao_bid) AS l_b, utilizador_user_id " +
                                "FROM licitacao " +
                                "WHERE leilao_artigo_leilao_id = ?" +
                                "GROUP BY utilizador_user_id");
                        ps2.setInt(1, (int) payload.get("leilao_id"));
                        ResultSet rows2 = ps2.executeQuery();
                        if (rows2.next()) {
                            String msg = "Your bid has been overtaken on Leilao " + (int) payload.get("leilao_id");
                            notifyUser(rows2.getInt("utilizador_user_id"), msg);
                        }
                        else {
                            logger.debug("Something failed sending message to the owner of Leilao");
                        }

                        PreparedStatement ul = conn.prepareStatement("UPDATE leilao_artigo " +
                                "SET leilao_currentbid = ? " +
                                "WHERE leilao_id = ?");
                        ul.setObject(1, bid, java.sql.Types.FLOAT);
                        ul.setInt(2, (int) payload.get("leilao_id"));

                        ul.executeUpdate();
                        conn.commit();

                        PreparedStatement il = conn.prepareStatement(""
                                + "INSERT INTO licitacao (licitacao_bid,utilizador_user_id,leilao_artigo_leilao_id) "
                                + "VALUES (?,?,?)");

                        il.setObject(1, bid, java.sql.Types.FLOAT);
                        il.setInt(2, user.getUserID());
                        il.setInt(3, (int) payload.get("leilao_id"));

                        il.executeUpdate();
                        conn.commit();

                        logger.debug("Current Bid updated");
                        return "Current Bid Updated";
                    }
                }
            } catch (SQLException ex) {
                logger.error("Error in DB", ex);
                try {
                    conn.rollback();
                } catch (SQLException ex1) {
                    logger.warn("Couldn't rollback", ex);
                }
            } catch (ClassCastException exception) {
                logger.error("Error Casting", exception);
                erro = "Um dos valores foi inserido incorretamente";
                try {
                    conn.rollback();
                } catch (SQLException ex1) {
                    logger.warn("Couldn't rollback", ex1);
                }
            } finally {
                try {
                    conn.close();
                } catch (SQLException ex) {
                    logger.error("Error in DB", ex);
                }
            }
        }
        else{
            logger.debug("Couldn't validate token");
            erro = "Couldn't validate token.";
        }
        return erro;
    }


    /**
     * PUT
     * Editar propriedades de um leilão
     */

    @PutMapping(value = "/dbproj/leilao/{leilaoId}", consumes = "application/json")
    @ResponseBody
    public String editLeilao(@RequestBody Map<String, Object> payload, @PathVariable("leilaoId") int leilaoID,@RequestHeader(value = "Authorization",required = false)String token) {
        logger.info("### PUT /dbproj/leilao/{leilaoId} - Editar propriedades de um leilão");
        logger.debug("- Editar leilao (ID): {}", leilaoID);
        logger.debug("Payload: {}", payload);
        String erro = "Failed";

        Connection conn = RestServiceApplication.getConnection();
        if (conn == null) {
            logger.debug("DB Problem");
            return "DB Problem";
        }

        if(token == null){
            logger.debug("Didn't get any token");
            return "Didn't get any token";
        }
        User user = validateToken(token);
        if (user.isValid()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * " +
                    "FROM leilao_artigo " +
                    "WHERE leilao_id = ?")) {
                ps.setInt(1, leilaoID);

                ResultSet rows = ps.executeQuery();

                if (rows.next()) {
                    if(!rows.getBoolean("leilao_authorized")){
                        logger.debug("This Leilao has been banned");
                        return "This Leilao has been banned";
                    }
                    Timestamp currenttime = new Timestamp(System.currentTimeMillis());
                    SimpleDateFormat current_date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    if (rows.getTimestamp("leilao_end_date").compareTo(Timestamp.valueOf(current_date.format(currenttime))) < 0) {
                        logger.debug("This Leilao has already finished");
                        return "This Leilao has already finished";
                    } else if (rows.getFloat("leilao_currentbid") != 0 && rows.getFloat("leilao_minprice") != (double) (payload.get("leilao_minprice"))) {
                        logger.debug("You can not change the minprice, bids have already been made");
                        return "You can not change the minprice, bids have already been made";
                    }else if(user.userID != rows.getInt("utilizador_user_id")){
                        logger.debug("You are not the owner of this auction!");
                        return "You are not the owner of this auction!";
                    }
                    else {
                        PreparedStatement up = conn.prepareStatement(""
                                + "INSERT INTO atualizacao (leilao_end_date,leilao_minprice,leilao_currentbid,leilao_authorized,leilao_artigo_leilao_id)"
                                + "VALUES (?,?,?,?,?)");
                        up.setObject(1, rows.getTimestamp("leilao_end_date"), java.sql.Types.TIMESTAMP);
                        up.setObject(2, rows.getFloat("leilao_minprice"), java.sql.Types.FLOAT);
                        up.setObject(3, rows.getFloat("leilao_currentbid"), java.sql.Types.FLOAT);
                        up.setBoolean(4, rows.getBoolean("leilao_authorized"));
                        up.setInt(5, rows.getInt("leilao_id"));

                        int affectedRows1 = up.executeUpdate();
                        conn.commit();
                        if (affectedRows1 == 1) {
                            logger.debug("Error saving update in Leilao");
                        }

                        PreparedStatement ul = conn.prepareStatement("UPDATE leilao_artigo " +
                                "SET leilao_end_date = ?,leilao_minprice = ?,artigo_artigo_name = ?, artigo_artigo_description = ? " +
                                "WHERE leilao_id = ?");
                        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss");
                        java.util.Date utilStartDate = dateFormat.parse((String) payload.get("leilao_end_date"));
                        java.sql.Date sqlStartDate = new java.sql.Date(utilStartDate.getTime());
                        Timestamp timestamp = new java.sql.Timestamp(sqlStartDate.getTime());
                        ul.setTimestamp(1, timestamp);
                        ul.setObject(2, payload.get("leilao_minprice"), java.sql.Types.FLOAT);
                        ul.setString(3, (String) payload.get("artigo_artigo_name"));
                        ul.setString(4, (String) payload.get("artigo_artigo_description"));
                        ul.setInt(5, leilaoID);

                        int affectedRows2 = ul.executeUpdate();
                        conn.commit();
                        if (affectedRows2 == 1) {
                            logger.debug("Leilao Edited");
                            return "Leilao Edited";
                        } else {
                            logger.debug("Error editing Leilao");
                            return "Error editing Leilao";
                        }
                    }
                } else {
                    logger.debug("There is no Leilao with the given ID");
                }
            } catch (SQLException ex) {
                logger.error("Error in DB", ex);
                try {
                    conn.rollback();
                } catch (SQLException ex1) {
                    logger.warn("Couldn't rollback", ex);
                }
            } catch (ParseException e) {
                e.printStackTrace();
            } catch (ClassCastException exception) {
                logger.error("Error Casting", exception);
                erro = "One of the values has been sent incorrectly";
                try {
                    conn.rollback();
                } catch (SQLException ex1) {
                    logger.warn("Couldn't rollback", ex1);
                }
            } finally {
                try {
                    conn.close();
                } catch (SQLException ex) {
                    logger.error("Error in DB", ex);
                }
            }
        }
        else{
            logger.debug("Couldn't validate token");
            erro = "Couldn't validate token.";
        }
        return erro;
    }


    /**
     * POST
     * colocar mensagem num moral
     */

    @PostMapping(value = "/dbproj/message/{leilaoId}", consumes = "application/json")
    @ResponseBody
    public String postMessage(@RequestBody Map<String, Object> payload,@PathVariable("leilaoId") int leilaoID,@RequestHeader(value = "Authorization",required = false)String token) {
        logger.info("### POST /dbproj/message/{leilaoId} - Colocar mensagem");
        logger.debug("- New Mensagem");
        logger.debug("Payload: {}", payload);
        String erro = "Failed";

        Connection conn = RestServiceApplication.getConnection();
        if (conn == null) {
            logger.debug("DB Problem");
            return "DB Problem";
        }

        if(token == null){
            logger.debug("Didn't get any token");
            return "Didn't get any token";
        }
        User user = validateToken(token);
        if (user.isValid()) {
            try (PreparedStatement ps = conn.prepareStatement(""
                    + "SELECT leilao_authorized, utilizador_user_id FROM leilao_artigo WHERE leilao_id = ?")) {
                ps.setInt(1, leilaoID);
                ResultSet rows = ps.executeQuery();
                if (rows.next()) {
                    if (rows.getBoolean("leilao_authorized")) {
                        PreparedStatement sm = conn.prepareStatement(""
                                + "INSERT INTO mural(message,leilao_artigo_leilao_id,utilizador_user_id)"
                                + "VALUES (?,?,?)");
                        sm.setString(1, (String) payload.get("message"));
                        sm.setInt(2, leilaoID);
                        sm.setInt(3, user.getUserID());

                        int affectedRows = sm.executeUpdate();

                        String msg = "New message on Leilao " + leilaoID + ": " + payload.get("message");
                        notifyUser(rows.getInt("utilizador_user_id"), msg);
                        PreparedStatement ps3 = conn.prepareStatement("SELECT DISTINCT utilizador_user_id " +
                                "FROM mural " +
                                "WHERE leilao_artigo_leilao_id = ?");
                        ps3.setInt(1, leilaoID);
                        ResultSet rows2 = ps3.executeQuery();
                        boolean flag = true;
                        while (rows2.next()) {
                            flag = false;
                            notifyUser(rows2.getInt("utilizador_user_id"), msg);
                        }
                        if (flag) {
                            logger.debug("No users have sent messages in Mural");
                        }

                        conn.commit();
                        if (affectedRows == 1) {
                            logger.debug("New message in mural");
                            return "New message in mural";
                        } else {
                            logger.debug("Error sending message");
                            return "Error sending message";
                        }
                    }
                    else {
                        logger.debug("The Leilao given has been banned");
                        return "The Leilao given has been banned";
                    }
                }
                else {
                    logger.debug("There is no Leilao with the given ID");
                    return "There is no Leilao with the given ID";
                }
            } catch (SQLException ex) {
                logger.error("Error in DB", ex);
                try {
                    conn.rollback();
                } catch (SQLException ex1) {
                    logger.warn("Couldn't rollback", ex);
                }
            } catch (ClassCastException exception) {
                logger.error("Error Casting", exception);
                erro = "One of the values has been sent incorrectly";
                try {
                    conn.rollback();
                } catch (SQLException ex1) {
                    logger.warn("Couldn't rollback", ex1);
                }
            } finally {
                try {
                    conn.close();
                } catch (SQLException ex) {
                    logger.error("Error in DB", ex);
                }
            }
        }
        else{
            logger.debug("Couldn't validate token");
            erro = "Couldn't validate token.";
        }
        return erro;
    }


    public void notifyUser(int user_id, String msg) {
        logger.debug("- Notify User");

        Connection conn = RestServiceApplication.getConnection();
        if (conn == null) {
            logger.debug("DB Problem");
        }

        try (PreparedStatement ps = conn.prepareStatement("SELECT user_ban FROM utilizador WHERE user_id = ?")) {
            ps.setInt(1, user_id);
            ResultSet rows = ps.executeQuery();
            if (rows.next()) {
                if (!rows.getBoolean("user_ban")) {
                    PreparedStatement sm = conn.prepareStatement(""
                            + "INSERT INTO caixa_mensagens(message,utilizador_user_id)"
                            + "VALUES (?,?)");
                    sm.setString(1, msg);
                    sm.setInt(2, user_id);
                    int affectedRows = sm.executeUpdate();
                    conn.commit();
                    if (affectedRows == 1) {
                        logger.debug("New message in Caixa_Mensagens");
                    } else {
                        logger.debug("Something failed sending message in Caixa_Mensagens");
                    }
                }
                else {
                    logger.debug("The User is banned");
                }
            } else {
                logger.debug("There is no User with the given ID");
            }
            conn.commit();
        } catch (SQLException ex) {
            logger.error("Error in DB", ex);
            try {
                conn.rollback();
            } catch (SQLException ex1) {
                logger.warn("Couldn't rollback", ex);
            }
        } finally {
            try {
                conn.close();
            } catch (SQLException ex) {
                logger.error("Error in DB", ex);
            }
        }
    }


    /**
     * GET
     * Ver mensagens na Caixa de Entrada
     */

    @GetMapping(value = "/dbproj/caixa_mensagens", produces = "application/json")
    @ResponseBody
    public List<Map<String, Object>> getCaixaMensagens(@RequestHeader(value = "Authorization",required = false)String token) {
        logger.info("### GET /dbproj/caixa_mensagens - Ver mensagens na Caixa de Entrada");
        logger.debug("- Caixa de Mensagens");

        List<Map<String, Object>> payload = new ArrayList<>();
        Connection conn = RestServiceApplication.getConnection();
        if (conn == null) {
            logger.debug("DB Problem");
            return payload;
        }
        if(token == null){
            logger.debug("Didn't get any token");
            Map<String, Object> content = new HashMap<>();
            content.put("Couldn't validate token.","");
            payload.add(content);
            return payload;
        }

        User user = validateToken(token);
        if (user.isValid()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT message, utilizador_user_id " +
                    "FROM caixa_mensagens " +
                    "WHERE utilizador_user_id = ?")) {
                ps.setInt(1, user.userID);
                ResultSet rows = ps.executeQuery();
                boolean flag = true;
                while (rows.next()) {
                    flag = false;
                    Map<String, Object> content = new HashMap<>();
                    logger.debug("'utilizador_user_id': {}, 'message': {}",
                            rows.getInt("utilizador_user_id"), rows.getString("message")
                    );
                    content.put("Número do User", rows.getInt("utilizador_user_id"));
                    content.put("Mensagem", rows.getString("message"));
                    payload.add(content);
                }
                if (flag) {
                    logger.debug("There are no messages in Caixa_Mensagens");
                }
            } catch (SQLException ex) {
                logger.error("Error in DB", ex);
            }
        }
        else{
            logger.debug("Couldn't validate token");
            Map<String, Object> content = new HashMap<>();
            content.put("Couldn't validate token.","");
            payload.add(content);
        }
        return payload;
    }


    /**
     * PUT
     * Termino do Leilao
     */

    @PutMapping(value = "/dbproj/CheckLeilao", consumes = "application/json")
    @ResponseBody
    public String checkLeilao(@RequestBody Map<String, Object> payload) {
        logger.info("### PUT /dbproj/CheckLeilao - ver se o leilao terminou");
        logger.debug("- Check leilao (ID): {}", payload.get("leilao_id"));

        String erro = "Failed";
        Connection conn = RestServiceApplication.getConnection();
        if (conn == null) {
            logger.debug("DB Problem");
            return "DB Problem";
        }

        try (PreparedStatement ps = conn.prepareStatement("SELECT * " +
                "FROM leilao_artigo " +
                "WHERE leilao_id = ?")) {
            ps.setInt(1, (int) payload.get("leilao_id"));

            ResultSet rows = ps.executeQuery();

            if (rows.next()) {
                if(!rows.getBoolean("leilao_ended")){
                    Timestamp currenttime = new Timestamp(System.currentTimeMillis());
                    SimpleDateFormat current_date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    if (rows.getTimestamp("leilao_end_date").compareTo(Timestamp.valueOf(current_date.format(currenttime))) < 0) {
                        logger.debug("This Leilao has finished closing it");

                        PreparedStatement up = conn.prepareStatement(""
                                + "INSERT INTO atualizacao (leilao_end_date,leilao_minprice,leilao_currentbid,leilao_authorized,leilao_artigo_leilao_id)"
                                + "VALUES (?,?,?,?,?)");
                        up.setObject(1, rows.getTimestamp("leilao_end_date"), java.sql.Types.TIMESTAMP);
                        up.setObject(2, rows.getFloat("leilao_minprice"), java.sql.Types.FLOAT);
                        up.setObject(3, rows.getFloat("leilao_currentbid"), java.sql.Types.FLOAT);
                        up.setBoolean(4, rows.getBoolean("leilao_authorized"));
                        up.setInt(5, rows.getInt("leilao_id"));

                        int affectedRows1 = up.executeUpdate();

                        if (affectedRows1 != 1) {
                            logger.debug("Error saving update in Leilao");
                        }

                        PreparedStatement ul = conn.prepareStatement("UPDATE leilao_artigo " +
                                "SET leilao_ended = ?" +
                                "WHERE leilao_id = ?");
                        ul.setBoolean(1, true);
                        ul.setInt(2, (int) payload.get("leilao_id"));

                        int affectedRows2 = ul.executeUpdate();
                        conn.commit();
                        if (affectedRows2 == 1) {
                            logger.debug("Leilao Ended");
                            return "Leilao Ended";
                        } else {
                            logger.debug("Leilao hasnt ended");
                            erro = "Leilao hasnt ended";
                        }
                    } else {
                        logger.debug("Leilao hasnt ended");
                        erro = "Leilao hasnt ended";
                    }
                }
                else {
                    logger.debug("Leilao has already been closed");
                    erro = "Leilao has aleady been closed";
                }
            }
            else {
                logger.debug("There is no Leilao with the given ID");
                erro = "There is no Leilao with the given ID";
            }
        } catch (SQLException ex) {
            logger.error("Error in DB", ex);
            try {
                conn.rollback();
            } catch (SQLException ex1) {
                logger.warn("Couldn't rollback", ex);
            }
        } catch (ClassCastException exception) {
            logger.error("Error Casting", exception);
            erro = "One of the values has been sent incorrectly";
            try {
                conn.rollback();
            } catch (SQLException ex1) {
                logger.warn("Couldn't rollback", ex1);
            }
        } finally {
            try {
                conn.close();
            } catch (SQLException ex) {
                logger.error("Error in DB", ex);
            }
        }
        return erro;
    }


    /**
     * PUT
     * Banir um Leilão que ainda esteja a decorrer
     */
    @PutMapping(value = "/dbproj/leilao/admin/cancel", consumes = "application/json")
    @ResponseBody
    public String cancelLeilao(@RequestBody Map<String, Object> payload, @RequestHeader(value = "Authorization",required = false) String token) {
        logger.info("### PUT /dbproj/leilao/admin/cancel - Cancelar um leilão por admin");
        String erro;
        int leilaoID = (int)payload.get("leilao_id");
        if(token == null){
            logger.debug("Didn't get any token");
            return "Didn't get any token";
        }
        User user = validateToken(token);
        erro = cancelLeilaoNotif(user, leilaoID);
        return erro;
    }

    public String cancelLeilaoNotif(User user, int leilaoID) {
        Connection conn = RestServiceApplication.getConnection();
        if (conn == null) {
            logger.debug("DB Problem");
            return "DB Problem";
        }

        String erro = "Failed";
        if (user.isValid()) {
            if (user.getUserType().equals("admin")) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT * " +
                        "FROM leilao_artigo " +
                        "WHERE leilao_id = ?")) {
                    ps.setInt(1, leilaoID);

                    ResultSet rows = ps.executeQuery();

                    if (rows.next()) {
                        Timestamp currenttime = new Timestamp(System.currentTimeMillis());
                        SimpleDateFormat current_date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


                        if (rows.getTimestamp("leilao_end_date").compareTo(Timestamp.valueOf(current_date.format(currenttime))) < 0) {
                            logger.debug("This Leilao has already finished");
                            return "This Leilao has already finished";

                        }
                        else if(!rows.getBoolean("leilao_authorized")){
                            logger.debug("This Leilao has already been banned");
                            return "This Leilao has already been banned";
                        }

                        else {
                            PreparedStatement up = conn.prepareStatement(""
                                    + "INSERT INTO atualizacao (leilao_end_date,leilao_minprice,leilao_currentbid,leilao_authorized,leilao_artigo_leilao_id)"
                                    + "VALUES (?,?,?,?,?)");

                            up.setObject(1, rows.getTimestamp("leilao_end_date"), java.sql.Types.TIMESTAMP);
                            up.setObject(2, rows.getFloat("leilao_minprice"), java.sql.Types.FLOAT);
                            up.setObject(3, rows.getFloat("leilao_currentbid"), java.sql.Types.FLOAT);
                            up.setBoolean(4, rows.getBoolean("leilao_authorized"));
                            up.setInt(5, leilaoID);

                            int affectedRows1 = up.executeUpdate();

                            if (affectedRows1 != 1) {
                                logger.debug("Error saving update in Leilao");
                            }
                            else{
                                PreparedStatement ul = conn.prepareStatement("UPDATE leilao_artigo " +
                                        "SET leilao_authorized = ? " +
                                        "WHERE leilao_id = ?");
                                ul.setBoolean(1,false);
                                ul.setInt(2,rows.getInt("leilao_id"));
                                int affectedRows2 = ul.executeUpdate();
                                if(affectedRows2 != 1){
                                    logger.debug("Error executing the ban on Auction");
                                }
                                else{
                                    PreparedStatement ps1 = conn.prepareStatement(""
                                            + "SELECT utilizador_user_id FROM leilao_artigo WHERE leilao_id = ?");
                                    ps1.setInt(1, leilaoID);
                                    ResultSet row = ps.executeQuery();
                                    if (row.next()) {
                                        notifyUser(row.getInt("utilizador_user_id"), "The auction with the id " + leilaoID +
                                                " has been cancelled!");

                                        PreparedStatement ps2 = conn.prepareStatement("SELECT licitacao.utilizador_user_id " +
                                                "FROM licitacao " +
                                                "WHERE licitacao.leilao_artigo_leilao_id = ? " +
                                                "UNION SELECT mural.utilizador_user_id " +
                                                "FROM mural " +
                                                "WHERE mural.leilao_artigo_leilao_id = ?");
                                        ps2.setInt(1, leilaoID);
                                        ps2.setInt(2, leilaoID);
                                        ResultSet user_id = ps2.executeQuery();
                                        while (user_id.next()) {
                                            notifyUser(user_id.getInt("utilizador_user_id"), "The auction with the id " + leilaoID +
                                                    " has been cancelled!");
                                            System.out.println(user_id.getInt("utilizador_user_id"));
                                        }
                                        //if (commit_conn)
                                        conn.commit();
                                        return "Leilao has been banned and the users notified";
                                    }
                                    else {
                                        logger.debug("Something failed getting User ID");
                                    }
                                }
                            }
                        }
                    } else {
                        logger.debug("There is no Leilao with the given ID");
                    }
                } catch (SQLException ex) {
                    logger.error("Error in DB", ex);
                    try {
                        conn.rollback();
                    } catch (SQLException ex1) {
                        logger.warn("Couldn't rollback", ex);
                    }
                }catch (ClassCastException exception) {
                    logger.error("Error Casting", exception);
                    erro = "One of the values has been sent incorrectly";
                    try {
                        conn.rollback();
                    } catch (SQLException ex1) {
                        logger.warn("Couldn't rollback", ex1);
                    }
                } finally {
                    try {
                        conn.close();
                    } catch (SQLException ex) {
                        logger.error("Error in DB", ex);
                    }
                }
            } else {
                erro = "You do not have permissions to execute that command!";
            }
        } else {
            logger.debug("Couldn't validate token");
            erro = "Couldn't validate token.";
        }
        return erro;
    }




    /**
     * PUT
     * Banir permanentemente um	 utilizador
     */
    @PutMapping(value = "/dbproj/user/admin/ban", consumes = "application/json")
    @ResponseBody
    public String banUser(@RequestBody Map<String, Object> payload, @RequestHeader(value = "Authorization",required = false) String token) {
        logger.info("### PUT /dbproj/user/admin/ban - Banir um User por um admin");
        logger.debug("- Banir um User: {}", payload);
        String erro = "Failed";
        int userID = (int)payload.get("user_id");

        Connection conn = RestServiceApplication.getConnection();
        if (conn == null) {
            logger.debug("DB Problem");
            return "DB Problem";
        }

        if(token == null){
            logger.debug("Didn't get any token");
            return "Didn't get any token";
        }
        User user = validateToken(token);
        if (user.isValid()) {
            if (user.getUserType().equals("admin")) {
                if (user.getUserID() == userID) {
                    logger.debug("You cant ban yourself");
                    erro = "You cant ban yourself";
                }
                else {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT user_ban FROM utilizador WHERE user_id = ?")) {
                        ps.setInt(1, userID);
                        ResultSet affected = ps.executeQuery();
                        if (affected.next()) {
                            if (!affected.getBoolean("user_ban")) {
                                PreparedStatement ul = conn.prepareStatement("UPDATE utilizador " +
                                        "SET user_ban = ? " +
                                        "WHERE user_id = ?");
                                ul.setObject(1, true);
                                ul.setInt(2, userID);
                                /*int affected = */
                                ul.executeUpdate();
                                logger.debug("User has been Banned");
                                PreparedStatement ps2 = conn.prepareStatement("SELECT leilao_id " +
                                        "FROM leilao_artigo " +
                                        "WHERE utilizador_user_id = ? ");
                                ps2.setInt(1, userID);
                                ResultSet leilao_id = ps2.executeQuery();

                                boolean flag = true;
                                while (leilao_id.next()) {
                                    flag = false;
                                    int l_id = leilao_id.getInt("leilao_id");   //Cancela todos os leilÃµes
                                    cancelLeilaoNotif(user, l_id/*, conn, false*/);
                                }
                                if (flag) {
                                    logger.debug("There are no Leiloes with the given user_name");
                                }

                                PreparedStatement ps3 = conn.prepareStatement("SELECT DISTINCT leilao_artigo_leilao_id " +
                                        "FROM licitacao " +
                                        "WHERE utilizador_user_id = ?");
                                ps3.setInt(1, userID);
                                ResultSet leilao_id1 = ps3.executeQuery();

                                boolean flag1 = true;
                                while (leilao_id1.next()) {
                                    flag1 = false;
                                    int l_id1 = leilao_id1.getInt("leilao_artigo_leilao_id");   //Muda a highest bid do leilao.
                                    changeHighestBid(user, userID, l_id1/*, conn*/);
                                }
                                if (flag1) {
                                    logger.debug("There are no Leiloes with the given user_name");
                                }

                                conn.commit();

                                logger.debug("User has been successfully banned and the necessary actions have been taken");
                                return "User has been successfully banned and the necessary actions have been taken";
                            } else {
                                logger.debug("User has already been banned");
                                erro = "User has already been banned";
                            }
                        } else {
                            logger.debug("User not found");
                            erro = "User not found";
                        }
                    } catch (SQLException ex) {
                        logger.error("Error in DB", ex);
                        try {
                            conn.rollback();
                        } catch (SQLException ex1) {
                            logger.warn("Couldn't rollback", ex);
                        }
                    } catch (ClassCastException exception) {
                        logger.error("Error Casting", exception);
                        erro = "One of the values has been sent incorrectly\n";
                        try {
                            conn.rollback();
                        } catch (SQLException ex1) {
                            logger.warn("Couldn't rollback", ex1);
                        }
                    } finally {
                        try {
                            conn.close();
                        } catch (SQLException ex) {
                            logger.error("Error in DB", ex);
                        }
                    }
                }
            } else {
                erro = "You do not have permissions to execute that command!";
            }
        } else {
            logger.debug("Couldn't validate token");
            erro = "Couldn't validate token.";
        }
        return erro;
    }


    public void changeHighestBid(User user,int userID, int leilaoID) {
        Connection conn = RestServiceApplication.getConnection();
        if (conn == null) {
            logger.debug("DB Problem");
        }

        try (PreparedStatement ps1 = conn.prepareStatement("SELECT max(licitacao_bid) AS m_bid " +
                "FROM licitacao " +
                "WHERE utilizador_user_id = ? " +
                "AND leilao_artigo_leilao_id = ?")) {
            ps1.setInt(1, userID);              //bid do user banido mais alta;
            ps1.setInt(2, leilaoID);

            ResultSet banned = ps1.executeQuery();
            if(banned.next()){
                int bannedBid = banned.getInt("m_bid");

                PreparedStatement ps2 = conn.prepareStatement("SELECT max(licitacao_bid) AS max_bid " +
                        "FROM licitacao " +
                        "WHERE leilao_artigo_leilao_id = ? ");
                ps2.setInt(1, leilaoID);                //max bid do leilao
                ResultSet maxBid = ps2.executeQuery();
                if(maxBid.next()){
                    if (maxBid.getFloat("max_bid") == bannedBid) {
                        // A bid banida era a mais alta do leilao
                        PreparedStatement ps3 = conn.prepareStatement("UPDATE licitacao " +
                                "SET licitacao_valid = false " +
                                "WHERE licitacao_bid = ? AND leilao_artigo_leilao_id = ?");
                        ps3.setObject(1,maxBid.getFloat("max_bid"), java.sql.Types.FLOAT);
                        ps3.setInt(2,leilaoID);
                        ps3.executeUpdate();
                        PreparedStatement ps4 = conn.prepareStatement("SELECT max(licitacao_bid) AS l_val "
                                + "FROM licitacao " + "WHERE licitacao_valid = true AND leilao_artigo_leilao_id = ?" );
                        ps4.setInt(1,leilaoID);
                        ResultSet l =  ps4.executeQuery();
                        if(l.next()){
                            PreparedStatement ps5 = conn.prepareStatement("UPDATE leilao_artigo"
                                    + " SET leilao_currentbid = ?" + " WHERE leilao_id = ?" );
                            ps5.setObject(1,l.getFloat("l_val"), Types.FLOAT);
                            ps5.setInt(2,leilaoID);
                            ps5.executeUpdate();
                        }
                        else{
                            PreparedStatement ps5 = conn.prepareStatement("UPDATE leilao_artigo"
                                    + " SET leilao_currentbid = ?" + " WHERE leilao_id = ?" );
                            ps5.setFloat(1,0);
                            ps5.setInt(2,leilaoID);
                            ps5.executeUpdate();
                        }
                    } else {
                        PreparedStatement ps3 = conn.prepareStatement("UPDATE licitacao " +
                                "SET licitacao_valid = false " +
                                "WHERE licitacao_bid >= ? AND licitacao_bid != ? " +
                                " AND leilao_artigo_leilao_id = ?");
                        ps3.setObject(1,bannedBid, java.sql.Types.FLOAT);
                        ps3.setObject(2,maxBid.getFloat("max_bid"), java.sql.Types.FLOAT);
                        ps3.setInt(3,leilaoID);
                        ps3.executeUpdate();

                        PreparedStatement ps4 = conn.prepareStatement("UPDATE licitacao " +
                                "SET licitacao_bid = ? " +
                                "WHERE licitacao_bid = ?" +
                                " AND leilao_artigo_leilao_id = ?");
                        ps4.setObject(1,bannedBid, java.sql.Types.FLOAT);
                        ps4.setObject(2,maxBid.getFloat("max_bid"), java.sql.Types.FLOAT);
                        ps4.setInt(3,leilaoID);
                        ps4.executeUpdate();

                        PreparedStatement ps5 = conn.prepareStatement("UPDATE leilao_artigo"
                                + " SET leilao_currentbid = ?" + " WHERE leilao_id = ?" );
                        ps5.setObject(1,bannedBid, java.sql.Types.FLOAT);
                        ps5.setInt(2,leilaoID);
                        ps5.executeUpdate();
                    }

                    PreparedStatement ps = conn.prepareStatement(""
                            + "SELECT utilizador_user_id FROM leilao_artigo WHERE leilao_id = ?");
                    ps.setInt(1, leilaoID);
                    ResultSet rows = ps.executeQuery();

                    PreparedStatement sm = conn.prepareStatement(""
                            + "INSERT INTO mural(message,leilao_artigo_leilao_id,utilizador_user_id)"
                            + "VALUES (?,?,?)");
                    sm.setString(1, "Unfortunately this auction has been modified and some bids were changed" +
                            "due to the ban of a user.");
                    sm.setInt(2, leilaoID);
                    sm.setInt(3, user.getUserID());

                    sm.executeUpdate();

                    if (rows.next()) {
                        String msg = "New message on Leilao " + leilaoID + ": " + "Unfortunately this auction has been modified and some bids were changed" +
                                "due to the ban of a user.";
                        notifyUser(rows.getInt("utilizador_user_id"), msg);

                        PreparedStatement ps3 = conn.prepareStatement("SELECT DISTINCT utilizador_user_id " +
                                "FROM mural " +
                                "WHERE leilao_artigo_leilao_id = ?");
                        ps3.setInt(1, leilaoID);
                        ResultSet rows2 = ps3.executeQuery();
                        boolean flag = true;
                        while (rows2.next()) {
                            flag = false;
                            notifyUser(rows2.getInt("utilizador_user_id"), msg);
                        }
                        if (flag) {
                            logger.debug("No users have sent messages in Mural");
                        }
                    }
                    else {
                        logger.debug("Something failed getting the owner of the auction");
                    }
                }
                else
                    logger.debug("Something failed getting max bid");
            }
            else{
                logger.debug("Something failed getting banned bid");
            }
        } catch (SQLException ex) {
            logger.error("Error in DB", ex);
            try {
                conn.rollback();
            } catch (SQLException ex1) {
                logger.warn("Couldn't rollback", ex);
            }
        } catch (ClassCastException exception) {
            logger.error("Error Casting", exception);
            try {
                conn.rollback();
            } catch (SQLException ex1) {
                logger.warn("Couldn't rollback", ex1);
            }
        } finally {
            try {
                conn.close();
            } catch (SQLException ex) {
                logger.error("Error in DB", ex);
            }
        }
    }


    /**
     * GET
     * Obter estatísticas de atividade na aplicação
     */

    @GetMapping(value = "/dbproj/estatisticas/admin", produces = "application/json")
    @ResponseBody
    public String getEstatisticas(@RequestHeader(value = "Authorization",required = false)String token) {
        logger.info("### GET /dbproj/estatisticas/admin - Obter estatísticas de atividade na aplicação");
        logger.debug("- Estatisticas");

        String string = "";
        Connection conn = RestServiceApplication.getConnection();
        if (conn == null) {
            logger.debug("DB Problem");
            return "DB Problem";
        }

        if(token == null){
            logger.debug("Didn't get any token");
            return "Didn't get any token";
        }

        int[][] topLeiloesCriados = new int[10][2];
        int[][] topVencedores = new int[10][2];
        int totalLeiloes = 0;

        User user = validateToken(token);
        if (user.isValid()) {
            if (user.getUserType().equals("admin")) {
                try (Statement stmt = conn.createStatement()) {
                    ResultSet rows = stmt.executeQuery("SELECT utilizador_user_id, COUNT(utilizador_user_id) as n_l " +
                            "FROM leilao_artigo " +
                            "GROUP BY utilizador_user_id " +
                            "ORDER BY n_l DESC");
                    int counter = 0;
                    boolean flag = true;
                    while (rows.next() && counter < 10) {
                        flag = false;
                        int user_id = rows.getInt("utilizador_user_id");
                        int num_leiloes = rows.getInt("n_l");
                        topLeiloesCriados[counter][0] = user_id;
                        topLeiloesCriados[counter][1] = num_leiloes;
                        counter++;
                    }
                    if (flag) {
                        logger.debug("There are no Leiloes");
                    }


                    ResultSet rows2 = stmt.executeQuery("SELECT DISTINCT leilao_id " +
                            "FROM leilao_artigo " +
                            "WHERE leilao_ended = true AND leilao_authorized = true " +
                            "AND leilao_currentbid != 0");
                    ArrayList<Integer> leiloesAcabados = new ArrayList<>();
                    counter = 0;
                    flag = true;
                    while (rows2.next()) {
                        flag = false;
                        leiloesAcabados.add(rows2.getInt("leilao_id"));
                    }
                    if (flag) {
                        logger.debug("There are no Leiloes");
                    }
                    ArrayList<ArrayList<Integer>> aux = new ArrayList<>();
                    int i, j;
                    for (i=0; i<leiloesAcabados.size(); i++) {
                        PreparedStatement ps1 = conn.prepareStatement("SELECT max(licitacao_bid), utilizador_user_id " +
                                "FROM licitacao " +
                                "WHERE leilao_artigo_leilao_id = ? AND licitacao_valid = true");
                        ps1.setInt(1, leiloesAcabados.get(i));
                        ResultSet rows3 = ps1.executeQuery();
                        if (rows.next()) {
                            int indice = -1;
                            for (j=0; j<aux.size(); j++) {
                                if (aux.get(j).get(0) == rows3.getInt("utilizador_user_id")) {
                                    indice = j;
                                    break;
                                }
                            }
                            if (indice == -1) {
                                ArrayList<Integer> aux2 = new ArrayList<>();
                                aux2.add(rows3.getInt("utilizador_user_id"));
                                aux2.add(1);
                                aux.add(aux2);
                            }
                            else {
                                aux.get(indice).set(1, aux.get(indice).get(1) + 1);
                            }
                        }
                        else {
                            logger.debug("Something failed getting max Licitacao from Leilao");
                        }
                    }

                    for (i=0; i<10; i++) {
                        int max_value = 0;
                        int indice = 0;
                        for (j=0; j<aux.size(); j++) {
                            if (aux.get(j).get(1) > max_value) {
                                indice = j;
                                max_value = aux.get(j).get(1);
                            }
                        }
                        if (max_value != 0) {
                            topVencedores[i][0] = aux.get(indice).get(0);
                            topVencedores[i][1] = aux.get(indice).get(1);
                            aux.remove(indice);
                        }
                    }

                    ResultSet rows4 = stmt.executeQuery("SELECT leilao_end_date " +
                            "FROM leilao_artigo " +
                            "WHERE leilao_authorized = true");

                    Timestamp currenttime = new Timestamp(System.currentTimeMillis());
                    SimpleDateFormat current_date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    Timestamp time = new Timestamp(System.currentTimeMillis() - 10*24*60*60*1000);
                    SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                    flag = true;
                    while (rows4.next()) {
                        flag = false;
                        if (rows4.getTimestamp("leilao_end_date").compareTo(Timestamp.valueOf(date.format(time))) > 0 &&
                                rows4.getTimestamp("leilao_end_date").compareTo(Timestamp.valueOf(current_date.format(currenttime))) < 0)
                            totalLeiloes++;
                    }
                    if (flag) {
                        logger.debug("There are no Leiloes");
                    }

                    string += "Top 10 utilizadores com mais leiloes criados\n";
                    for (i=0;i<10;i++) {
                        if (topLeiloesCriados[i][0] != 0)
                            string += "User " + topLeiloesCriados[i][0] + ": criou " + topLeiloesCriados[i][1] + " leiloes\n";
                    }
                    string += "\nTop 10 utilizadores que mais leiloes venceram\n";
                    for (i=0;i<10;i++) {
                        if (topVencedores[i][0] != 0)
                            string += "User " + topVencedores[i][0] + ": venceu " + topVencedores[i][1] + " leiloes\n";
                    }
                    string += "\nNúmero total de leiloes dos ultimos 10 dias\n    -> " + totalLeiloes;


                } catch (SQLException ex) {
                    logger.error("Error in DB", ex);
                }
            }
        }
        else{
            logger.debug("Couldn't validate token");
            Map<String, Object> content = new HashMap<>();
            content.put("Couldn't validate token.","");
        }
        return string;
    }
}