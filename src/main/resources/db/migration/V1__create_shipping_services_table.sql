CREATE SEQUENCE shipping_services_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE shipping_services (
    id NUMBER(19) DEFAULT shipping_services_seq.NEXTVAL PRIMARY KEY,
    name VARCHAR2(150) NOT NULL,
    description VARCHAR2(500),
    rate NUMBER(10,2) NOT NULL,
    capacity NUMBER(10) NOT NULL,
    active NUMBER(1) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version NUMBER(10) DEFAULT 0 NOT NULL
);
