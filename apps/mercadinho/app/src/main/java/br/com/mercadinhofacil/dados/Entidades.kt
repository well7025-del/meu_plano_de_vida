package br.com.mercadinhofacil.dados

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

// Datas são guardadas em milissegundos (System.currentTimeMillis) e dinheiro em centavos.

@Entity(tableName = "produtos", indices = [Index("codigoBarras"), Index("nome")])
data class Produto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val codigoBarras: String? = null,
    val categoria: String = "",
    /** "un", "kg", "l", "pct", "cx" */
    val unidade: String = "un",
    val preco: Long = 0,
    val custo: Long = 0,
    val estoque: Double = 0.0,
    val estoqueMinimo: Double = 0.0,
    val descricao: String = "",
    /** Nome do arquivo em files/fotos */
    val foto: String? = null,
    /** Outros nomes que o cliente fala, separados por vírgula ("refri, coca") */
    val apelidos: String = "",
    val noCatalogo: Boolean = true,
    val ativo: Boolean = true,
    val criadoEm: Long = System.currentTimeMillis(),
)

@Entity(tableName = "clientes", indices = [Index("nome")])
data class Cliente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val telefone: String = "",
    val observacao: String = "",
    /** Limite de fiado em centavos; 0 = sem limite */
    val limite: Long = 0,
    val ativo: Boolean = true,
    val criadoEm: Long = System.currentTimeMillis(),
)

object TipoConta {
    const val DINHEIRO = "DINHEIRO"
    const val PIX = "PIX"
    const val CARTAO = "CARTAO"
    const val OUTRA = "OUTRA"
}

@Entity(tableName = "contas")
data class Conta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val tipo: String = TipoConta.OUTRA,
    val ativa: Boolean = true,
    val ordem: Int = 0,
)

object StatusVenda {
    const val PAGA = "PAGA"
    /** Pix gerado, esperando cair na conta */
    const val PENDENTE = "PENDENTE"
    const val FIADO = "FIADO"
    const val CANCELADA = "CANCELADA"
}

@Entity(tableName = "vendas", indices = [Index("data"), Index("clienteId"), Index("status")])
data class Venda(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val data: Long = System.currentTimeMillis(),
    val subtotal: Long,
    val desconto: Long = 0,
    val total: Long,
    /** Nome de br.com.mercadinhofacil.core.voz.FormaPagamento */
    val forma: String,
    val contaId: Long? = null,
    val clienteId: Long? = null,
    val status: String,
    val valorRecebido: Long? = null,
    val observacao: String = "",
    val canceladaEm: Long? = null,
    val motivoCancelamento: String = "",
)

@Entity(tableName = "itens_venda", indices = [Index("vendaId"), Index("produtoId")])
data class ItemVenda(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vendaId: Long,
    val produtoId: Long?,
    val nome: String,
    val unidade: String = "un",
    val quantidade: Double,
    val precoUnitario: Long,
    val custoUnitario: Long,
)

object TipoMovimento {
    const val VENDA = "VENDA"
    const val RECEBIMENTO_FIADO = "RECEBIMENTO_FIADO"
    const val DESPESA = "DESPESA"
    /** Compra de mercadoria para revenda (vira estoque, não entra como despesa no lucro) */
    const val COMPRA = "COMPRA"
    const val TRANSFERENCIA = "TRANSFERENCIA"
    /** Dinheiro que o dono colocou no caixa */
    const val ENTRADA = "ENTRADA"
    /** Dinheiro que o dono tirou para si */
    const val RETIRADA = "RETIRADA"
    /** Diferença encontrada ao fechar o caixa */
    const val AJUSTE = "AJUSTE"

    fun rotulo(tipo: String) = when (tipo) {
        VENDA -> "Venda"
        RECEBIMENTO_FIADO -> "Recebimento de fiado"
        DESPESA -> "Despesa"
        COMPRA -> "Compra de mercadoria"
        TRANSFERENCIA -> "Transferência"
        ENTRADA -> "Entrada de dinheiro"
        RETIRADA -> "Retirada do dono"
        AJUSTE -> "Ajuste de caixa"
        else -> tipo
    }
}

@Entity(tableName = "movimentos", indices = [Index("contaId"), Index("data"), Index("vendaId"), Index("clienteId"), Index("tipo")])
data class Movimento(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val data: Long = System.currentTimeMillis(),
    val contaId: Long,
    val tipo: String,
    /** Sempre positivo; [entrada] diz o sentido */
    val valor: Long,
    val entrada: Boolean,
    val descricao: String = "",
    val categoria: String = "",
    val vendaId: Long? = null,
    val clienteId: Long? = null,
    /** Liga as duas pontas de uma transferência */
    val grupo: String? = null,
    val cancelado: Boolean = false,
)

object TipoEstoque {
    const val ENTRADA = "ENTRADA"
    const val VENDA = "VENDA"
    const val PERDA = "PERDA"
    const val AJUSTE = "AJUSTE"
    const val CANCELAMENTO = "CANCELAMENTO"
}

@Entity(tableName = "movimentos_estoque", indices = [Index("produtoId"), Index("data")])
data class MovimentoEstoque(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val data: Long = System.currentTimeMillis(),
    val produtoId: Long,
    /** Positivo entra, negativo sai */
    val quantidade: Double,
    val tipo: String,
    val custoUnitario: Long = 0,
    val observacao: String = "",
    val vendaId: Long? = null,
)

@Entity(tableName = "fechamentos")
data class Fechamento(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val data: Long = System.currentTimeMillis(),
    val contaId: Long,
    val saldoSistema: Long,
    val valorContado: Long,
    val observacao: String = "",
)

/** Registro de tudo que foi criado, alterado ou cancelado. */
@Entity(tableName = "historico", indices = [Index("data")])
data class Historico(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val data: Long = System.currentTimeMillis(),
    val acao: String,
    val detalhe: String,
)

// ---- Consultas compostas

data class VendaComItens(
    @Embedded val venda: Venda,
    @Relation(parentColumn = "id", entityColumn = "vendaId") val itens: List<ItemVenda>,
)

data class ClienteComDivida(
    @Embedded val cliente: Cliente,
    val divida: Long,
    val fiadoMaisAntigo: Long?,
)

data class ContaComSaldo(
    @Embedded val conta: Conta,
    val saldo: Long,
)

data class GiroProduto(val produtoId: Long, val quantidade: Double, val ultimaVenda: Long)
