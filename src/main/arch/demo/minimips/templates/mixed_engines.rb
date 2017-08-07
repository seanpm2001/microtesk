#
# Copyright 2017 ISP RAS (http://www.ispras.ru)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

require_relative 'minimips_base'

#
# Description:
#
# This test template demonstrates how to o mix constraints related to ALU, BPU and MMU
# in a single instruction sequence.
#
class MixedEnginesTemplate < MiniMipsBaseTemplate

  def initialize
    super
    set_option_value 'default-test-data', false
  end

  def pre
    super

    data {
      org 0x00010000
      align 8
      # Arrays to store test data for branch instructions.
      label :branch_data_0
      word 0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,
           0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,
           0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,
           0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,
           0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,
           0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,
           0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,
           0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0
      label :branch_data_1
      word 0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,
           0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,
           0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,
           0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,
           0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,
           0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,
           0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,
           0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0,    0x0, 0x0, 0x0, 0x0
    }

    stream_preparator(:data_source => 'REG', :index_source => 'REG') {
      init {
        la index_source, start_label
      }

      read {
        lw data_source, 0x0, index_source
        addiu index_source, index_source, 4
      }

      write {
        sw data_source, 0x0, index_source
        addiu index_source, index_source, 4
      }
    }

    buffer_preparator(:target => 'L1') {
      la t0, address
      lw t1, 0, t0
    }

    buffer_preparator(:target => 'L2') {
      la t0, address
      lw t1, 0, t0
    }

    memory_preparator(:size => 32) {
      la t0, address
      prepare t1, value
      sw t0, 0, t1
    }
  end

  def run
    org 0x00020000

    # Stream  Label            Data  Addr  Size
    stream   :branch_data_0,   s0,   s2,   128
    stream   :branch_data_1,   s1,   s3,   128

    # A branch structure is as follows:
    #
    #  0: NOP
    #  1: if (BGEZ) then goto 4
    #  2: ADD (IntegerOverflow)
    #  3: goto 5
    #  4: ADD(Normal) + LW (MMU)
    #  5: if (BLTZ) then goto 0

    # Parameter 'branch_exec_limit' bounds the number of executions of a single branch:
    #   the default value is 1.
    # Parameter 'trace_count_limit' bounds the number of execution traces to be created:
    #   the default value is -1 (no limitation).
    sequence(
        :engines => {
            :combinator => 'product',
            :branch => {:branch_exec_limit => 3,
                        :trace_count_limit => -1},
            :memory => {:classifier => 'event-based',
                        :page_mask => 0x0fff,
                        :align => 4,
                        :count => 5}
        }) {
      label :start
        nop
        bgez s0, :normal do
          situation('bgez-if-then', :engine => :branch, :stream => 'branch_data_0')
        end
        nop

      label :overflow
        add t0, t1, t2 do situation('IntegerOverflow') end
        j :finish do
          situation('b-goto', :engine => :branch)
        end
        nop

      label :normal
        add t0, t3, t4 do situation('normal') end
        lw s4, 0, t5 do situation('memory', :engine => :memory, :base => 'lw.address') end
        lw s5, 0, t6 do situation('memory', :engine => :memory, :base => 'lw.address') end

      label :finish
        nop
        bltz s1, :start do
          situation('bltz-if-then', :engine => :branch, :stream => 'branch_data_1')
        end
        nop
    }.run
  end

end
