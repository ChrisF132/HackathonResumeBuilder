package org.example;

public class UserInfo {
    private String name, email, phone, cityState, about, quals;

    public UserInfo() {
        name = email = phone = cityState = about = quals = "";
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCityState() {
        return cityState;
    }

    public void setCityState(String cityState) {
        this.cityState = cityState;
    }

    public String getAbout() {
        return about;
    }

    public void setAbout(String about) {
        this.about = about;
    }

    public String getQuals() {
        return quals;
    }

    public void setQuals(String quals) {
        this.quals = quals;
    }
}
