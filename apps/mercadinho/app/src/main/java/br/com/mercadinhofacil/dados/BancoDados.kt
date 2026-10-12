package br.com.mercadinhofacil.dados

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Produto::class, Cliente::class, Conta::class, Venda::class, ItemVenda::class,
        Movimento::class, MovimentoEstoque::class, Fechamento::class, Historico::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class BancoDados : RoomDatabase() {
    abstract fun dao(): MercadinhoDao

    companion object {
        const val NOME = "mercadinho.db"

        fun abrir(context: Context): BancoDados =
            Room.databaseBuilder(context.applicationContext, BancoDados::class.java, NOME)
                // Versões futuras devem trazer Migration; nunca apagar os dados do comerciante.
                .build()
    }
}
