CREATE OR REPLACE FUNCTION create_or_update_grafana_dashboard(id text, title text, dashboard text, user_name text) RETURNS void AS
$$
BEGIN
  RAISE WARNING 'dashboard: % title: %', dashboard, title;

  BEGIN

    INSERT INTO zzm_data.grafana_dashboard(gd_id, gd_title, gd_tags, gd_dashboard, gd_created_by, gd_last_modified_by)
         SELECT id, title, '{}'::text[], dashboard::jsonb, user_name, user_name;

  EXCEPTION WHEN UNIQUE_VIOLATION THEN

    UPDATE zzm_data.grafana_dashboard
       SET gd_title = title,
           gd_dashboard = dashboard::jsonb,
           gd_last_modified = now(),
           gd_last_modified_by = user_name
     WHERE gd_id = id;

  END;
END;
$$ LANGUAGE PLPGSQL VOLATILE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION get_grafana_dashboards(OUT id TEXT, OUT title TEXT, OUT dashboard TEXT, OUT "user" TEXT) RETURNS SETOF record AS
$$
  SELECT gd_id id, gd_title title, gd_dashboard::text dashboard, gd_created_by "user"
    FROM zzm_data.grafana_dashboard;
$$ LANGUAGE SQL VOLATILE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION get_grafana_dashboard(INOUT id text, OUT title text, OUT dashboard text, OUT "user" TEXT) RETURNS SETOF record AS
$$
  SELECT gd_id id, gd_title title, gd_dashboard::text dashboard, gd_created_by "user"
    FROM zzm_data.grafana_dashboard
   WHERE gd_id = id;
$$ LANGUAGE SQL VOLATILE SECURITY DEFINER;