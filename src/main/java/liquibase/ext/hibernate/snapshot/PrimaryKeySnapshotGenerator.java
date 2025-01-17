package liquibase.ext.hibernate.snapshot;

import liquibase.Scope;
import liquibase.exception.DatabaseException;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Column;
import liquibase.structure.core.Index;
import liquibase.structure.core.PrimaryKey;
import liquibase.structure.core.Table;
import org.hibernate.sql.Alias;

import java.util.ServiceLoader;

public class PrimaryKeySnapshotGenerator extends HibernateSnapshotGenerator {

    private static final int PKNAMELENGTH = 63;
    private static final String PK = "PK";

    // This is the same Alias as in org.hibernate.mapping.PersistentClass.PK_ALIAS
    private static final Alias PK_ALIAS_15 = new Alias(15, PK);
    private static final Alias NEW_PK_ALIAS = new Alias(PKNAMELENGTH, "");
    private static final PrimaryKeyAlias CUSTOM_PK_ALIAS = loadPrimaryKeyAlias();

    private static PrimaryKeyAlias loadPrimaryKeyAlias() {
        ServiceLoader<PrimaryKeyAlias> loader = ServiceLoader.load(PrimaryKeyAlias.class);
        if (!loader.iterator().hasNext()) {
            return null;
        }
        return loader.iterator().next();
    }

    public PrimaryKeySnapshotGenerator() {
        super(PrimaryKey.class, new Class[]{Table.class});
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot) throws DatabaseException, InvalidExampleException {
        return example;
    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot) throws DatabaseException, InvalidExampleException {
        if (!snapshot.getSnapshotControl().shouldInclude(PrimaryKey.class)) {
            return;
        }
        if (foundObject instanceof Table) {
            Table table = (Table) foundObject;
            org.hibernate.mapping.Table hibernateTable = findHibernateTable(table, snapshot);
            if (hibernateTable == null) {
                return;
            }
            org.hibernate.mapping.PrimaryKey hibernatePrimaryKey = hibernateTable.getPrimaryKey();
            if (hibernatePrimaryKey != null) {
                PrimaryKey pk = new PrimaryKey();
                String hbnPrimaryKeyName = hibernatePrimaryKey.getName();
                String hbnTableName = hibernateTable.getName();
                if (CUSTOM_PK_ALIAS != null) {
                    hbnPrimaryKeyName = CUSTOM_PK_ALIAS.toAliasString(hbnTableName);
                    Scope.getCurrentScope().getLog(getClass()).warning("Changing hibernate primary key name to " + hbnPrimaryKeyName);
                } else {
                    /*
                     * pk name is probably truncated and maybe a duplicate in case
                     * of tables with long prefixes
                     */
                    if (hbnPrimaryKeyName != null && hbnPrimaryKeyName.length() == 15 && hbnPrimaryKeyName.equals(PK_ALIAS_15.toAliasString(hbnTableName))) {
                        Scope.getCurrentScope().getLog(getClass()).warning("Hibernate primary key name is probably truncated. " + hbnPrimaryKeyName);
                        String newAlias = NEW_PK_ALIAS.toAliasString(hbnTableName);
                        int newAliasLength = newAlias.length();
                        if (newAliasLength > 15) {
                            if (newAliasLength == PKNAMELENGTH) {
                                String suffix = "_" + Integer.toHexString(hbnTableName.hashCode()).toUpperCase() + "_" + PK;
                                hbnPrimaryKeyName = newAlias.substring(0, PKNAMELENGTH - suffix.length()) + suffix;
                            }
                            else {
                                hbnPrimaryKeyName = newAlias;
                            }
                            Scope.getCurrentScope().getLog(getClass()).warning("Changing hibernate primary key name to " + hbnPrimaryKeyName);
                        }
                    }
                }
                pk.setName(hbnPrimaryKeyName);
                pk.setTable(table);
                for (Object hibernateColumn : hibernatePrimaryKey.getColumns()) {
                    pk.getColumns().add(new Column(((org.hibernate.mapping.Column) hibernateColumn).getName()).setRelation(table));
                }

                Scope.getCurrentScope().getLog(getClass()).info("Found primary key " + pk.getName());
                table.setPrimaryKey(pk);
                Index index = new Index();
                index.setName("IX_" + pk.getName());
                index.setRelation(table);
                index.setColumns(pk.getColumns());
                index.setUnique(true);
                pk.setBackingIndex(index);
                table.getIndexes().add(index);
            }
        }
    }

}
