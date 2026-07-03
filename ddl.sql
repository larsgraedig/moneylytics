create table public.accounts (
                                 tableoid oid not null,
                                 cmax cid not null,
                                 xmax xid not null,
                                 cmin cid not null,
                                 xmin xid not null,
                                 ctid tid not null,
                                 id bigint primary key not null,
                                 iban character varying(255) not null,
                                 name character varying(255) not null,
                                 user_id bigint not null,
                                 foreign key (user_id) references public.users (id)
                                     match simple on update no action on delete no action
);
create unique index uk3f53h5n0b23c536sa5fuxunus on accounts using btree (iban, user_id);

create table public.budget_transactions (
                                            tableoid oid not null,
                                            cmax cid not null,
                                            xmax xid not null,
                                            cmin cid not null,
                                            xmin xid not null,
                                            ctid tid not null,
                                            id bigint primary key not null,
                                            amount numeric(19,2),
                                            budget_id bigint not null,
                                            transaction_id bigint not null,
                                            foreign key (transaction_id) references public.transactions (id)
                                                match simple on update no action on delete cascade,
                                            foreign key (budget_id) references public.budgets (id)
                                                match simple on update no action on delete cascade
);
create unique index uk5lh02hxaa618t60hcytmjq0h3 on budget_transactions using btree (budget_id, transaction_id);

create table public.budgets (
                                tableoid oid not null,
                                cmax cid not null,
                                xmax xid not null,
                                cmin cid not null,
                                xmin xid not null,
                                ctid tid not null,
                                id bigint primary key not null,
                                name character varying(255) not null,
                                note text,
                                target_amount numeric(19,2),
                                user_id bigint not null,
                                foreign key (user_id) references public.users (id)
                                    match simple on update no action on delete no action
);

create table public.categories (
                                   tableoid oid not null,
                                   cmax cid not null,
                                   xmax xid not null,
                                   cmin cid not null,
                                   xmin xid not null,
                                   ctid tid not null,
                                   id bigint primary key not null,
                                   name character varying(255) not null,
                                   subcategory character varying(255) not null,
                                   user_id bigint not null,
                                   category_group character varying(255),
                                   foreign key (user_id) references public.users (id)
                                       match simple on update no action on delete no action
);
create unique index uk5n6504ophnwe6hsjp36dn926i on categories using btree (name, subcategory, user_id);
create unique index categories_unique_idx on categories using btree (name, (COALESCE(category_group), subcategory, user_id);

    create table public.csv_profiles (
    tableoid oid not null,
    cmax cid not null,
    xmax xid not null,
    cmin cid not null,
    xmin xid not null,
    ctid tid not null,
    id bigint primary key not null,
    fingerprint character varying(255) not null,
    mapping_json text not null,
    user_id bigint not null
    );

    create table public.ignored_transactions (
    tableoid oid not null,
    cmax cid not null,
    xmax xid not null,
    cmin cid not null,
    xmin xid not null,
    ctid tid not null,
    id bigint primary key not null,
    fingerprint character varying(64) not null,
    user_id bigint not null,
    foreign key (user_id) references public.users (id)
    match simple on update no action on delete no action
    );
    create unique index ukarrma69ttol1b9eenthofoebo on ignored_transactions using btree (fingerprint, user_id);

    create table public.thresholds (
    tableoid oid not null,
    cmax cid not null,
    xmax xid not null,
    cmin cid not null,
    xmin xid not null,
    ctid tid not null,
    id bigint primary key not null,
    category character varying(255) not null,
    critical numeric(19,2),
    notice numeric(19,2),
    period character varying(255) not null,
    subcategory character varying(255),
    warning numeric(19,2),
    user_id bigint not null,
    category_group character varying(255),
    foreign key (user_id) references public.users (id)
    match simple on update no action on delete no action
    );

    create table public.transaction_groups (
    tableoid oid not null,
    cmax cid not null,
    xmax xid not null,
    cmin cid not null,
    xmin xid not null,
    ctid tid not null,
    id bigint primary key not null default nextval('transaction_groups_id_seq'::regclass),
    user_id bigint not null,
    name character varying(255),
    comment text,
    foreign key (user_id) references public.users (id)
    match simple on update no action on delete cascade
    );

    create table public.transaction_offsets (
    tableoid oid not null,
    cmax cid not null,
    xmax xid not null,
    cmin cid not null,
    xmin xid not null,
    ctid tid not null,
    id bigint primary key not null default nextval('transaction_offsets_id_seq'::regclass),
    transaction_a_id bigint not null,
    transaction_b_id bigint not null,
    amount numeric(19,4),
    foreign key (transaction_a_id) references public.transactions (id)
    match simple on update no action on delete cascade,
    foreign key (transaction_b_id) references public.transactions (id)
    match simple on update no action on delete cascade
    );
    create unique index uq_transaction_offsets on transaction_offsets using btree (transaction_a_id, transaction_b_id);
    create unique index uk8gdnftytdaxhxh6wep1eoyv3v on transaction_offsets using btree (transaction_a_id, transaction_b_id);

    create table public.transactions (
    tableoid oid not null,
    cmax cid not null,
    xmax xid not null,
    cmin cid not null,
    xmin xid not null,
    ctid tid not null,
    id bigint primary key not null,
    amount numeric(19,4) not null,
    booking_date date not null,
    category character varying(255),
    currency character varying(3) not null,
    fingerprint character varying(64) not null,
    subcategory character varying(255),
    value_date date not null,
    account_id bigint not null,
    user_id bigint not null,
    comment character varying(1000),
    purpose character varying(2000),
    accounting_date date not null,
    counterparty_name character varying(255),
    counterparty_iban character varying(34),
    category_group character varying(255),
    group_id bigint,
    foreign key (account_id) references public.accounts (id)
    match simple on update no action on delete no action,
    foreign key (user_id) references public.users (id)
    match simple on update no action on delete no action,
    foreign key (group_id) references public.transaction_groups (id)
    match simple on update no action on delete set null
    );
    create unique index uktlko6dnlc3ds2jcop242s59da on transactions using btree (fingerprint, account_id);

    create table public.users (
    tableoid oid not null,
    cmax cid not null,
    xmax xid not null,
    cmin cid not null,
    xmin xid not null,
    ctid tid not null,
    id bigint primary key not null,
    external_id character varying(255) not null,
    password_hash character varying(255),
    language character varying(10),
    default_account_id bigint,
    transactions_column_order character varying(500),
    foreign key (default_account_id) references public.accounts (id)
    match simple on update no action on delete no action
    );
    create unique index ukcup9hom3h5cte4btcq935d0uu on users using btree (external_id);

