export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[]

export type Database = {
  // Allows to automatically instantiate createClient with right options
  // instead of createClient<Database, { PostgrestVersion: 'XX' }>(URL, KEY)
  __InternalSupabase: {
    PostgrestVersion: "14.18"
  }
  public: {
    Tables: {
      clientes: {
        Row: {
          actualizado_en: string
          ciudad: string | null
          codigo: string
          id: string
          nombre: string
          zona: string | null
        }
        Insert: {
          actualizado_en?: string
          ciudad?: string | null
          codigo: string
          id?: string
          nombre: string
          zona?: string | null
        }
        Update: {
          actualizado_en?: string
          ciudad?: string | null
          codigo?: string
          id?: string
          nombre?: string
          zona?: string | null
        }
        Relationships: []
      }
      pedido_items: {
        Row: {
          cantidad: number
          id: number
          pedido_id: string
          precio_aplicado_bs: number | null
          precio_visto_bs: number
          problema: string | null
          producto_id: string
          subtotal_bs: number
        }
        Insert: {
          cantidad: number
          id?: number
          pedido_id: string
          precio_aplicado_bs?: number | null
          precio_visto_bs: number
          problema?: string | null
          producto_id: string
          subtotal_bs: number
        }
        Update: {
          cantidad?: number
          id?: number
          pedido_id?: string
          precio_aplicado_bs?: number | null
          precio_visto_bs?: number
          problema?: string | null
          producto_id?: string
          subtotal_bs?: number
        }
        Relationships: [
          {
            foreignKeyName: "pedido_items_pedido_id_fkey"
            columns: ["pedido_id"]
            isOneToOne: false
            referencedRelation: "pedidos"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pedido_items_producto_id_fkey"
            columns: ["producto_id"]
            isOneToOne: false
            referencedRelation: "productos"
            referencedColumns: ["id"]
          },
        ]
      }
      pedidos: {
        Row: {
          actualizado_en: string
          cliente_id: string
          creado_en_dispositivo: string
          estado: string
          id: string
          motivo: string | null
          nota_supervisor: string | null
          recibido_en: string
          resuelto_en: string | null
          resuelto_por: string | null
          total_bs: number
          vendedor_id: string
        }
        Insert: {
          actualizado_en?: string
          cliente_id: string
          creado_en_dispositivo: string
          estado: string
          id: string
          motivo?: string | null
          nota_supervisor?: string | null
          recibido_en?: string
          resuelto_en?: string | null
          resuelto_por?: string | null
          total_bs: number
          vendedor_id: string
        }
        Update: {
          actualizado_en?: string
          cliente_id?: string
          creado_en_dispositivo?: string
          estado?: string
          id?: string
          motivo?: string | null
          nota_supervisor?: string | null
          recibido_en?: string
          resuelto_en?: string | null
          resuelto_por?: string | null
          total_bs?: number
          vendedor_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "pedidos_cliente_id_fkey"
            columns: ["cliente_id"]
            isOneToOne: false
            referencedRelation: "clientes"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pedidos_resuelto_por_fkey"
            columns: ["resuelto_por"]
            isOneToOne: false
            referencedRelation: "perfiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pedidos_vendedor_id_fkey"
            columns: ["vendedor_id"]
            isOneToOne: false
            referencedRelation: "perfiles"
            referencedColumns: ["id"]
          },
        ]
      }
      perfiles: {
        Row: {
          activo: boolean
          codigo: string
          id: string
          nombre: string
          rol: string
        }
        Insert: {
          activo?: boolean
          codigo: string
          id: string
          nombre: string
          rol: string
        }
        Update: {
          activo?: boolean
          codigo?: string
          id?: string
          nombre?: string
          rol?: string
        }
        Relationships: []
      }
      productos: {
        Row: {
          actualizado_en: string
          codigo: string
          id: string
          nombre: string
          precio_bs: number
          stock: number
          unidad: string
        }
        Insert: {
          actualizado_en?: string
          codigo: string
          id?: string
          nombre: string
          precio_bs: number
          stock: number
          unidad: string
        }
        Update: {
          actualizado_en?: string
          codigo?: string
          id?: string
          nombre?: string
          precio_bs?: number
          stock?: number
          unidad?: string
        }
        Relationships: []
      }
    }
    Views: {
      [_ in never]: never
    }
    Functions: {
      enviar_pedido: {
        Args: {
          p_cliente_id: string
          p_creado_en: string
          p_id: string
          p_items: Json
        }
        Returns: Json
      }
      es_supervisor: { Args: never; Returns: boolean }
      es_vendedor_activo: { Args: never; Returns: boolean }
      resolver_pedido: {
        Args: {
          p_accion: string
          p_id: string
          p_items_ajustados?: Json
          p_nota?: string
        }
        Returns: Json
      }
    }
    Enums: {
      [_ in never]: never
    }
    CompositeTypes: {
      [_ in never]: never
    }
  }
}

type DatabaseWithoutInternals = Omit<Database, "__InternalSupabase">

type DefaultSchema = DatabaseWithoutInternals[Extract<keyof Database, "public">]

export type Tables<
  DefaultSchemaTableNameOrOptions extends
    | keyof (DefaultSchema["Tables"] & DefaultSchema["Views"])
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends (DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
        DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])
    : never) = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
      DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])[TableName] extends {
      Row: infer R
    }
    ? R
    : never
  : DefaultSchemaTableNameOrOptions extends keyof (DefaultSchema["Tables"] &
        DefaultSchema["Views"])
    ? (DefaultSchema["Tables"] &
        DefaultSchema["Views"])[DefaultSchemaTableNameOrOptions] extends {
        Row: infer R
      }
      ? R
      : never
    : never

export type TablesInsert<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends (DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never) = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Insert: infer I
    }
    ? I
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Insert: infer I
      }
      ? I
      : never
    : never

export type TablesUpdate<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends (DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never) = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Update: infer U
    }
    ? U
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Update: infer U
      }
      ? U
      : never
    : never

export type Enums<
  DefaultSchemaEnumNameOrOptions extends
    | keyof DefaultSchema["Enums"]
    | { schema: keyof DatabaseWithoutInternals },
  EnumName extends (DefaultSchemaEnumNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"]
    : never) = never,
> = DefaultSchemaEnumNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"][EnumName]
  : DefaultSchemaEnumNameOrOptions extends keyof DefaultSchema["Enums"]
    ? DefaultSchema["Enums"][DefaultSchemaEnumNameOrOptions]
    : never

export type CompositeTypes<
  PublicCompositeTypeNameOrOptions extends
    | keyof DefaultSchema["CompositeTypes"]
    | { schema: keyof DatabaseWithoutInternals },
  CompositeTypeName extends (PublicCompositeTypeNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"]
    : never) = never,
> = PublicCompositeTypeNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"][CompositeTypeName]
  : PublicCompositeTypeNameOrOptions extends keyof DefaultSchema["CompositeTypes"]
    ? DefaultSchema["CompositeTypes"][PublicCompositeTypeNameOrOptions]
    : never

export const Constants = {
  public: {
    Enums: {},
  },
} as const
