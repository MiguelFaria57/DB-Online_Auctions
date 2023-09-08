/*drop leilao_artigo;
drop utilizador;
drop atualizacao;
drop mural;
drop caixa_mensagens;
drop licitacao;*/

CREATE TABLE leilao_artigo (
                               leilao_id                 BIGINT,
                               leilao_end_date           TIMESTAMP NOT NULL,
                               leilao_minprice           FLOAT(8) NOT NULL,
                               leilao_currentbid         FLOAT(8),
                               leilao_ended              BOOL NOT NULL DEFAULT false,
                               leilao_authorized         BOOL NOT NULL DEFAULT true,
                               artigo_artigo_id          VARCHAR(512) UNIQUE NOT NULL,
                               artigo_artigo_name        VARCHAR(512) NOT NULL,
                               artigo_artigo_description VARCHAR(512) NOT NULL,
                               utilizador_user_id        BIGINT NOT NULL,
                               PRIMARY KEY(leilao_id)
);

CREATE TABLE utilizador (
                            user_id        BIGINT,
                            user_name      VARCHAR(512) UNIQUE NOT NULL,
                            user_email     VARCHAR(512) UNIQUE NOT NULL,
                            user_password  VARCHAR(512) NOT NULL,
                            user_ban       BOOL NOT NULL DEFAULT false,
                            user_type      VARCHAR(512) NOT NULL,
                            PRIMARY KEY(user_id)
);

CREATE TABLE atualizacao (
                             leilao_end_date         TIMESTAMP NOT NULL,
                             leilao_minprice         FLOAT(8) NOT NULL,
                             leilao_currentbid       FLOAT(8),
                             leilao_authorized       BOOL NOT NULL,
                             leilao_artigo_leilao_id BIGINT NOT NULL
);

CREATE TABLE mural (
                       message                 VARCHAR(512),
                       utilizador_user_id      BIGINT NOT NULL,
                       leilao_artigo_leilao_id BIGINT NOT NULL
);

CREATE TABLE caixa_mensagens (
                                 message            VARCHAR(512),
                                 utilizador_user_id BIGINT
);

CREATE TABLE licitacao (
                           licitacao_bid           BIGINT NOT NULL,
                           licitacao_valid         BOOL NOT NULL DEFAULT true,
                           utilizador_user_id      BIGINT NOT NULL,
                           leilao_artigo_leilao_id BIGINT NOT NULL
);


ALTER TABLE leilao_artigo ADD CONSTRAINT leilao_artigo_fk1 FOREIGN KEY (utilizador_user_id) REFERENCES utilizador(user_id);
ALTER TABLE atualizacao ADD CONSTRAINT atualizacao_fk1 FOREIGN KEY (leilao_artigo_leilao_id) REFERENCES leilao_artigo(leilao_id);
ALTER TABLE mural ADD CONSTRAINT mural_fk1 FOREIGN KEY (leilao_artigo_leilao_id) REFERENCES leilao_artigo(leilao_id);
ALTER TABLE mural ADD CONSTRAINT mural_fk2 FOREIGN KEY (utilizador_user_id) REFERENCES utilizador(user_id);
ALTER TABLE caixa_mensagens ADD CONSTRAINT caixa_mensagens_fk1 FOREIGN KEY (utilizador_user_id) REFERENCES utilizador(user_id);
ALTER TABLE licitacao ADD CONSTRAINT licitacao_fk1 FOREIGN KEY (utilizador_user_id) REFERENCES utilizador(user_id);
ALTER TABLE licitacao ADD CONSTRAINT licitacao_fk2 FOREIGN KEY (leilao_artigo_leilao_id) REFERENCES leilao_artigo(leilao_id);


/* Valores Teste */
INSERT INTO utilizador (user_id,user_name,user_email,user_password,user_ban,user_type)
VALUES (1, 'ADMIN', 'ADMIN', 'SVEAF', FALSE, 'admin'),
       (2,'Manuel_Antonio','a@email.com','hskkogjv',FALSE,'user'),
       (3,'Ana_Cristina','b@email.com','hskkogjvv',FALSE,'user'),
       (4,'Luis_Joao','c@email.com','hskkogjvvv',FALSE,'admin');

INSERT INTO leilao_artigo (leilao_id,leilao_end_date, leilao_minprice, leilao_currentbid,leilao_ended, leilao_authorized, artigo_artigo_id, artigo_artigo_name, artigo_artigo_description, utilizador_user_id)
VALUES (1,TO_TIMESTAMP('2021-05-30 18:00:00', 'YYYY-MM-DD HH24:MI:SS'),2.50,0,FALSE,TRUE,'dwaj9hg2wa','Capa de Telemovel','Capa de cor preta',2),
       (2,TO_TIMESTAMP('2021-06-18 12:00:00', 'YYYY-MM-DD HH24:MI:SS'),250.00,0,FALSE,TRUE,'d8waawdwa7','Anel','Anel de ouro branco',3),
       (3,TO_TIMESTAMP('2021-10-28 19:00:00', 'YYYY-MM-DD HH24:MI:SS'),50.00,0,FALSE,TRUE,'iwau734yah','Bola de Futebol','Oficial da Liga Europa',2);