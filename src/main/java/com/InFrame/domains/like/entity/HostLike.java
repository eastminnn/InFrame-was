package com.InFrame.domains.like.entity;

import com.InFrame.domains.host.entity.Host;
import com.InFrame.domains.user.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
// 한 유저는 한 호스트에 좋아요 하나 (동시 요청에도 DB가 보장)
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_host_like_user_host", columnNames = {"user_id", "host_id"}))
@Getter
@NoArgsConstructor
public class HostLike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private Host host;

    @Builder
    public HostLike(User user, Host host) {
        this.user = user;
        this.host = host;
    }
}
