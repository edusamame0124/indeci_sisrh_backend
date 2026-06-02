package com.indeci.user.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    Page<User> findByUsernameContainingIgnoreCase(String q, Pageable pageable);

    @Query("""
            select u
              from User u
             where (:q is null or lower(u.username) like lower(concat('%', :q, '%')))
               and (:status is null or upper(u.status) = upper(:status))
               and (
                    :sistema is null
                    or (:sistema = 'sisrh' and exists (
                        select ur
                          from UsuarioRol ur
                         where ur.userId = u.id
                           and upper(ur.sistema) = 'SISRH'
                    ))
                    or (:sistema <> 'sisrh' and exists (
                        select us
                          from UsuarioSistema us, Sistema s
                         where us.userId = u.id
                           and us.sistemaId = s.id
                           and us.activo = 1
                           and s.codigo = :sistema
                    ))
               )
            """)
    Page<User> searchAdminUsers(
            @Param("q") String q,
            @Param("status") String status,
            @Param("sistema") String sistema,
            Pageable pageable);
}
