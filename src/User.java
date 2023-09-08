package pt.uc.dei.bd2021;

public class User {
    String userType;
    int userID;
    boolean valid;

    public User(){}

    public User(String userType, int userID, boolean valid) {
        this.userType = userType;
        this.userID = userID;
        this.valid = valid;
    }

    public User(String userType, int userID) {
        this.userType = userType;
        this.userID = userID;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public int getUserID() {
        return userID;
    }

    public void setUserID(int userID) {
        this.userID = userID;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }
}
