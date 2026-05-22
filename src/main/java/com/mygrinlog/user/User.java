package com.mygrinlog.user;

import com.mygrinlog.common.jpa.BaseTimeEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_github_id", columnNames = "github_id"))
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_id", nullable = false, length = 100)
    private String githubId;

    @Column(name = "nickname", nullable = false, length = 100)
    private String nickname;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    protected User() {}

    public User(String githubId, String nickname, String avatarUrl) {
        this.githubId = githubId;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
    }

    public Long getId() { return id; }
    public String getGithubId() { return githubId; }
    public String getNickname() { return nickname; }
    public String getAvatarUrl() { return avatarUrl; }

    public void updateProfile(String nickname, String avatarUrl) {
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
    }
}
