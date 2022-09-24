create table sticky_roles
(
    user_id unsigned big int not null,
    role_id unsigned big int not null,
    primary key (user_id, role_id)
);